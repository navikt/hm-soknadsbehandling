package no.nav.hjelpemidler.soknad.mottak

import com.zaxxer.hikari.HikariDataSource
import io.ktor.application.Application
import io.ktor.application.ApplicationCall
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.auth.authenticate
import io.ktor.features.CallLogging
import io.ktor.features.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.jackson.JacksonConverter
import io.ktor.request.path
import io.ktor.routing.route
import io.ktor.routing.routing
import io.ktor.util.pipeline.PipelineContext
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.hjelpemidler.soknad.mottak.client.SøknadForBrukerClient
import no.nav.hjelpemidler.soknad.mottak.client.SøknadForBrukerClientImpl
import no.nav.hjelpemidler.soknad.mottak.client.SøknadForFormidlerClientImpl
import no.nav.hjelpemidler.soknad.mottak.db.InfotrygdStorePostgres
import no.nav.hjelpemidler.soknad.mottak.db.OrdreStorePostgres
import no.nav.hjelpemidler.soknad.mottak.db.SøknadStorePostgres
import no.nav.hjelpemidler.soknad.mottak.db.dataSourceFrom
import no.nav.hjelpemidler.soknad.mottak.db.migrate
import no.nav.hjelpemidler.soknad.mottak.db.waitForDB
import no.nav.hjelpemidler.soknad.mottak.service.DigitalSøknadEndeligJournalført
import no.nav.hjelpemidler.soknad.mottak.service.GodkjennSoknad
import no.nav.hjelpemidler.soknad.mottak.service.JournalpostSink
import no.nav.hjelpemidler.soknad.mottak.service.NyOrdrelinje
import no.nav.hjelpemidler.soknad.mottak.service.OppgaveSink
import no.nav.hjelpemidler.soknad.mottak.service.PapirSøknadEndeligJournalført
import no.nav.hjelpemidler.soknad.mottak.service.SlettSoknad
import no.nav.hjelpemidler.soknad.mottak.service.SoknadMedFullmaktDataSink
import no.nav.hjelpemidler.soknad.mottak.service.SoknadUtenFullmaktDataSink
import no.nav.hjelpemidler.soknad.mottak.service.SøknadsgodkjenningService
import no.nav.hjelpemidler.soknad.mottak.service.VedtaksresultatFraInfotrygd
import no.nav.hjelpemidler.soknad.mottak.service.hentSoknad
import no.nav.hjelpemidler.soknad.mottak.service.hentSoknaderForBruker
import no.nav.hjelpemidler.soknad.mottak.service.hentSoknaderForFormidler
import no.nav.hjelpemidler.soknad.mottak.tokenx.TokendingsServiceWrapper
import no.nav.tms.token.support.idporten.user.IdportenUserFactory
import no.nav.tms.token.support.tokendings.exchange.TokendingsServiceBuilder
import org.slf4j.event.Level
import java.util.Timer
import kotlin.concurrent.scheduleAtFixedRate
import kotlin.time.ExperimentalTime
import kotlin.time.minutes

private val logger = KotlinLogging.logger {}

@ExperimentalTime
fun main() {
    if (!waitForDB(10.minutes, Configuration)) {
        throw Exception("database never became available within the deadline")
    }

    val tokendingsService = TokendingsServiceBuilder.buildTokendingsService()
    val tokendingsServiceWrapper = TokendingsServiceWrapper(tokendingsService, "clientId")
    val søknadForBrukerClient = SøknadForBrukerClientImpl("url", tokendingsServiceWrapper)
    val søknadForFormidlerClient = SøknadForFormidlerClientImpl("url", tokendingsServiceWrapper)

    val ds: HikariDataSource = dataSourceFrom(Configuration)
    val store = SøknadStorePostgres(ds)
    val ordreStore = OrdreStorePostgres(ds)
    val infotrygdStore = InfotrygdStorePostgres(ds)

    RapidApplication.Builder(RapidApplication.RapidApplicationConfig.fromEnv(Configuration.rapidApplication))
        .withKtorModule { api(søknadForBrukerClient, søknadForFormidlerClient) }
        .build().apply {
            SoknadMedFullmaktDataSink(this, store)
            SoknadUtenFullmaktDataSink(this, store)
            SlettSoknad(this, store)
            GodkjennSoknad(this, store)
            startSøknadUtgåttScheduling(SøknadsgodkjenningService(store, this))
            JournalpostSink(this, store)
            OppgaveSink(this, store)
            DigitalSøknadEndeligJournalført(this, store, infotrygdStore)
            NyOrdrelinje(this, ordreStore, infotrygdStore)
            VedtaksresultatFraInfotrygd(this, infotrygdStore)
            PapirSøknadEndeligJournalført(this, store, infotrygdStore)
        }
        .apply {
            register(
                object : RapidsConnection.StatusListener {
                    override fun onStartup(rapidsConnection: RapidsConnection) {
                        migrate(Configuration)
                    }
                }
            )
        }.start()
}

internal fun Application.api(søknadForBrukerClient: SøknadForBrukerClient, søknadForFormidlerClient: SøknadForFormidlerClientImpl) {

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
        logger.info("markerer utgåtte søknader...")
        val antallUtgåtte = søknadsgodkjenningService.slettUtgåtteSøknader()
        logger.info("Antall utgåtte søknader: $antallUtgåtte")
    }
}

val PipelineContext<*, ApplicationCall>.idportenUser get() = IdportenUserFactory.createIdportenUser(call)
