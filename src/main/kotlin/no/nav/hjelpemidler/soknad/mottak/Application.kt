package no.nav.hjelpemidler.soknad.mottak

import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.hjelpemidler.soknad.mottak.aad.AzureClient
import no.nav.hjelpemidler.soknad.mottak.client.PdlClient
import no.nav.hjelpemidler.soknad.mottak.client.SøknadForRiverClientImpl
import no.nav.hjelpemidler.soknad.mottak.metrics.InfluxMetrics
import no.nav.hjelpemidler.soknad.mottak.river.DigitalSøknadAutomatiskJournalført
import no.nav.hjelpemidler.soknad.mottak.river.DigitalSøknadEndeligJournalført
import no.nav.hjelpemidler.soknad.mottak.river.DigitalSøknadEndeligJournalførtEtterTilbakeføring
import no.nav.hjelpemidler.soknad.mottak.river.GodkjennSoknad
import no.nav.hjelpemidler.soknad.mottak.river.HotsakOpprettet
import no.nav.hjelpemidler.soknad.mottak.river.JournalpostSink
import no.nav.hjelpemidler.soknad.mottak.river.NyHotsakOrdrelinje
import no.nav.hjelpemidler.soknad.mottak.river.NyInfotrygdOrdrelinje
import no.nav.hjelpemidler.soknad.mottak.river.OppgaveSink
import no.nav.hjelpemidler.soknad.mottak.river.PapirSøknadEndeligJournalført
import no.nav.hjelpemidler.soknad.mottak.river.SlettSoknad
import no.nav.hjelpemidler.soknad.mottak.river.SoknadMedFullmaktDataSink
import no.nav.hjelpemidler.soknad.mottak.river.SoknadUtenFullmaktDataSink
import no.nav.hjelpemidler.soknad.mottak.river.VedtaksresultatFraHotsak
import no.nav.hjelpemidler.soknad.mottak.river.VedtaksresultatFraInfotrygd
import no.nav.hjelpemidler.soknad.mottak.service.MonitoreringService
import no.nav.hjelpemidler.soknad.mottak.service.SøknadsgodkjenningService
import no.nav.hjelpemidler.soknad.mottak.wiremock.WiremockServer
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
    val søknadForRiverClient =
        SøknadForRiverClientImpl(baseUrlSoknadsbehandlingDb, azureClient, Configuration.azure.dbApiScope)
    val pdlClient = PdlClient(azureClient, Configuration.pdl.baseUrl, Configuration.pdl.apiScope)
    val influxMetrics = InfluxMetrics(pdlClient)

    MonitoreringService(søknadForRiverClient)

    RapidApplication.Builder(RapidApplication.RapidApplicationConfig.fromEnv(Configuration.rapidApplication))
        .build().apply {
            SoknadMedFullmaktDataSink(this, søknadForRiverClient, influxMetrics)
            SoknadUtenFullmaktDataSink(this, søknadForRiverClient, influxMetrics)
            SlettSoknad(this, søknadForRiverClient)
            GodkjennSoknad(this, søknadForRiverClient)
            startSøknadUtgåttScheduling(SøknadsgodkjenningService(søknadForRiverClient, this))
            JournalpostSink(this, søknadForRiverClient)
            OppgaveSink(this, søknadForRiverClient)
            DigitalSøknadEndeligJournalført(this, søknadForRiverClient)
            NyInfotrygdOrdrelinje(this, søknadForRiverClient)
            NyHotsakOrdrelinje(this, søknadForRiverClient)
            VedtaksresultatFraInfotrygd(this, søknadForRiverClient)
            PapirSøknadEndeligJournalført(this, søknadForRiverClient, influxMetrics)
            DigitalSøknadAutomatiskJournalført(this, søknadForRiverClient)
            VedtaksresultatFraHotsak(this, søknadForRiverClient)
            HotsakOpprettet(this, søknadForRiverClient)
            DigitalSøknadEndeligJournalførtEtterTilbakeføring(this, søknadForRiverClient)
        }
        .start()
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
