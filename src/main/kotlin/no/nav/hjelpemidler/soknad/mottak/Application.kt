package no.nav.hjelpemidler.soknad.mottak

import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.hjelpemidler.soknad.mottak.aad.AzureClient
import no.nav.hjelpemidler.soknad.mottak.client.InfotrygdProxyClientImpl
import no.nav.hjelpemidler.soknad.mottak.client.OebsClient
import no.nav.hjelpemidler.soknad.mottak.client.PdlClient
import no.nav.hjelpemidler.soknad.mottak.client.SøknadForRiverClientImpl
import no.nav.hjelpemidler.soknad.mottak.delbestilling.DelbestillingClient
import no.nav.hjelpemidler.soknad.mottak.delbestilling.DelbestillingOrdrelinjeStatus
import no.nav.hjelpemidler.soknad.mottak.delbestilling.DelbestillingStatus
import no.nav.hjelpemidler.soknad.mottak.metrics.Metrics
import no.nav.hjelpemidler.soknad.mottak.river.*
import no.nav.hjelpemidler.soknad.mottak.river.BehovsmeldingIkkeBehovForBrukerbekreftelseDataSink
import no.nav.hjelpemidler.soknad.mottak.river.BehovsmeldingTilBrukerbekreftelseDataSink
import no.nav.hjelpemidler.soknad.mottak.river.BestillingAvvistFraHotsak
import no.nav.hjelpemidler.soknad.mottak.river.BestillingFerdigstiltFraHotsak
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
import no.nav.hjelpemidler.soknad.mottak.river.VedtaksresultatFraHotsak
import no.nav.hjelpemidler.soknad.mottak.river.VedtaksresultatFraInfotrygd
import no.nav.hjelpemidler.soknad.mottak.service.SøknadsgodkjenningService
import no.nav.hjelpemidler.soknad.mottak.wiremock.WiremockServer
import java.util.Timer
import kotlin.concurrent.schedule
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

    val søknadForRiverClient =
        SøknadForRiverClientImpl(Configuration.soknadsbehandlingDb.baseUrl, azureClient, Configuration.azure.dbApiScope)
    val infotrygdProxyClient =
        InfotrygdProxyClientImpl(
            Configuration.infotrygdProxy.baseUrl,
            azureClient,
            Configuration.azure.infotrygdProxyScope
        )
    val pdlClient = PdlClient(azureClient, Configuration.pdl.baseUrl, Configuration.pdl.apiScope)
    val oebsClient = OebsClient(azureClient, Configuration.oebs.baseUrl, Configuration.oebs.apiScope)

    val delbestillingClient = DelbestillingClient(Configuration.delbestillingApi.baseUrl, azureClient, Configuration.azure.delbestillingApiScope)

    RapidApplication.Builder(RapidApplication.RapidApplicationConfig.fromEnv(Configuration.rapidApplication))
        .build().apply {
            val metrics = Metrics(this, pdlClient)
            BehovsmeldingIkkeBehovForBrukerbekreftelseDataSink(this, søknadForRiverClient, metrics)
            BehovsmeldingTilBrukerbekreftelseDataSink(this, søknadForRiverClient, metrics)
            SlettSoknad(this, søknadForRiverClient)
            GodkjennSoknad(this, søknadForRiverClient)
            startSøknadUtgåttScheduling(SøknadsgodkjenningService(søknadForRiverClient, this))
            JournalpostSink(this, søknadForRiverClient)
            OppgaveSink(this, søknadForRiverClient)
            DigitalSøknadEndeligJournalført(this, søknadForRiverClient)
            NyInfotrygdOrdrelinje(this, søknadForRiverClient, infotrygdProxyClient)
            NyHotsakOrdrelinje(this, søknadForRiverClient)
            VedtaksresultatFraInfotrygd(this, søknadForRiverClient, metrics)
            PapirSøknadEndeligJournalført(this, søknadForRiverClient, metrics)
            DigitalSøknadAutomatiskJournalført(this, søknadForRiverClient)
            VedtaksresultatFraHotsak(this, søknadForRiverClient)
            HotsakOpprettet(this, søknadForRiverClient)
            DigitalSøknadEndeligJournalførtEtterTilbakeføring(this, søknadForRiverClient)
            BestillingFerdigstiltFraHotsak(this, søknadForRiverClient)
            BestillingAvvistFraHotsak(this, søknadForRiverClient)
            // Delbestilling
            DelbestillingStatus(this, delbestillingClient)
            DelbestillingOrdrelinjeStatus(this, delbestillingClient)

            hentBrukerpassrollebytter(oebsClient)
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

private fun hentBrukerpassrollebytter(oebsClient: OebsClient) {
    val timer = Timer("hent-brukerpassrollebytter-task", true)

    timer.schedule(10000) {
        runBlocking {
            launch {
                logger.info("hentBrukerpassrollebytter henter fnr for varsel om tilgjengelig brukerpassbytte")
                val resultat = oebsClient.hentBrukerpassrollebytter()
                logger.info("hentBrukerpassrollebytter Antall fnr: ${resultat.size}")
            }
        }
    }
}
