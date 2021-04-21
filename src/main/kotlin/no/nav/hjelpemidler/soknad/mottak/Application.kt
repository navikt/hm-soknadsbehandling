package no.nav.hjelpemidler.soknad.mottak

import io.ktor.application.Application
import io.ktor.application.ApplicationCall
import io.ktor.application.install
import io.ktor.auth.authenticate
import io.ktor.features.CallLogging
import io.ktor.features.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.jackson.JacksonConverter
import io.ktor.request.path
import io.ktor.routing.route
import io.ktor.routing.routing
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.hjelpemidler.soknad.mottak.aad.AzureClient
import no.nav.hjelpemidler.soknad.mottak.client.SøknadForBrukerClient
import no.nav.hjelpemidler.soknad.mottak.client.SøknadForBrukerClientImpl
import no.nav.hjelpemidler.soknad.mottak.client.SøknadForFormidlerClientImpl
import no.nav.hjelpemidler.soknad.mottak.client.SøknadForRiverClientImpl
import no.nav.hjelpemidler.soknad.mottak.river.DigitalSøknadEndeligJournalført
import no.nav.hjelpemidler.soknad.mottak.river.GodkjennSoknad
import no.nav.hjelpemidler.soknad.mottak.river.JournalpostSink
import no.nav.hjelpemidler.soknad.mottak.river.NyOrdrelinje
import no.nav.hjelpemidler.soknad.mottak.river.OppgaveSink
import no.nav.hjelpemidler.soknad.mottak.river.PapirSøknadEndeligJournalført
import no.nav.hjelpemidler.soknad.mottak.river.SlettSoknad
import no.nav.hjelpemidler.soknad.mottak.river.SoknadMedFullmaktDataSink
import no.nav.hjelpemidler.soknad.mottak.river.SoknadUtenFullmaktDataSink
import no.nav.hjelpemidler.soknad.mottak.river.VedtaksresultatFraInfotrygd
import no.nav.hjelpemidler.soknad.mottak.service.SøknadsgodkjenningService
import no.nav.hjelpemidler.soknad.mottak.service.hentSoknad
import no.nav.hjelpemidler.soknad.mottak.service.hentSoknaderForBruker
import no.nav.hjelpemidler.soknad.mottak.service.hentSoknaderForFormidler
import no.nav.hjelpemidler.soknad.mottak.tokenx.TokendingsServiceWrapper
import no.nav.hjelpemidler.soknad.mottak.wiremock.WiremockServer
import no.nav.tms.token.support.tokendings.exchange.TokendingsServiceBuilder
import org.slf4j.event.Level
import java.util.Timer
import kotlin.concurrent.scheduleAtFixedRate
import kotlin.time.ExperimentalTime

private val logger = KotlinLogging.logger {}

@ExperimentalTime
fun main() {

    if (Configuration.application.profile == Profile.LOCAL) {
        WiremockServer(Configuration).startServer()
    }

    val azureClient = AzureClient(
        tenantUrl = "${Configuration.azure.tenantBaseUrl}/${Configuration.azure.tenantId}",
        clientId = Configuration.azure.clientId,
        clientSecret = Configuration.azure.clientSecret
    )

    val baseUrlSoknadsbehandlingDb = Configuration.soknadsbehandlingDb.baseUrl
    val tokendingsService = TokendingsServiceBuilder.buildTokendingsService()
    val tokendingsServiceWrapper = TokendingsServiceWrapper(tokendingsService, Configuration.tokenX.clientIdSoknadsbehandlingDb)
    val søknadForBrukerClient = SøknadForBrukerClientImpl(baseUrlSoknadsbehandlingDb, tokendingsServiceWrapper)
    val søknadForFormidlerClient = SøknadForFormidlerClientImpl(baseUrlSoknadsbehandlingDb, tokendingsServiceWrapper)
    val søknadForRiverClient =
        SøknadForRiverClientImpl(baseUrlSoknadsbehandlingDb, azureClient, Configuration.azure.dbApiScope)

    RapidApplication.Builder(RapidApplication.RapidApplicationConfig.fromEnv(Configuration.rapidApplication))
        .withKtorModule { api(søknadForBrukerClient, søknadForFormidlerClient) }
        .build().apply {
            SoknadMedFullmaktDataSink(this, søknadForRiverClient)
            SoknadUtenFullmaktDataSink(this, søknadForRiverClient)
            SlettSoknad(this, søknadForRiverClient)
            GodkjennSoknad(this, søknadForRiverClient)
            startSøknadUtgåttScheduling(SøknadsgodkjenningService(søknadForRiverClient, this))
            JournalpostSink(this, søknadForRiverClient)
            OppgaveSink(this, søknadForRiverClient)
            DigitalSøknadEndeligJournalført(this, søknadForRiverClient)
            NyOrdrelinje(this, søknadForRiverClient)
            VedtaksresultatFraInfotrygd(this, søknadForRiverClient)
            PapirSøknadEndeligJournalført(this, søknadForRiverClient)
        }
        .start()
}

internal fun Application.api(
    søknadForBrukerClient: SøknadForBrukerClient,
    søknadForFormidlerClient: SøknadForFormidlerClientImpl
) {

    install(CallLogging) {
        level = Level.INFO
        filter { it.request.path().startsWith("/api") }
    }

    install(ContentNegotiation) {
        register(ContentType.Application.Json, JacksonConverter(JacksonMapper.objectMapper))
    }

    val config = runBlocking { environment.config.load() }
    installAuthentication(config, Configuration)

    routing {
        route("/api") {
            authenticate("tokenX") {
                hentSoknad(søknadForBrukerClient)
                hentSoknaderForBruker(søknadForBrukerClient)
                hentSoknaderForFormidler(søknadForFormidlerClient)
            }
        }
    }
}

private fun startSøknadUtgåttScheduling(søknadsgodkjenningService: SøknadsgodkjenningService) {
    val timer = Timer("utgatt-soknader-task", true)

    timer.scheduleAtFixedRate(60000, 1000 * 60 * 60) {
        runBlocking {
            launch {
                logger.info("markerer utgåtte søknader...")
                val antallUtgåtte = søknadsgodkjenningService.slettUtgåtteSøknader()
                logger.info("Antall utgåtte søknader: $antallUtgåtte")
            }
        }
    }
}

fun ApplicationCall.token() = when {
    request.headers.contains(HttpHeaders.Authorization) -> {
        request.headers[HttpHeaders.Authorization]!!.substringAfter(" ")
    }
    else -> throw RuntimeException("")
}
