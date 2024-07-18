package no.nav.hjelpemidler.soknad.mottak

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.engine.apache.Apache
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.hjelpemidler.http.openid.azureADClient
import no.nav.hjelpemidler.soknad.mottak.client.InfotrygdProxyClient
import no.nav.hjelpemidler.soknad.mottak.client.PdlClient
import no.nav.hjelpemidler.soknad.mottak.client.SøknadForRiverClient
import no.nav.hjelpemidler.soknad.mottak.delbestilling.DelbestillingClient
import no.nav.hjelpemidler.soknad.mottak.delbestilling.DelbestillingOrdrelinjeStatus
import no.nav.hjelpemidler.soknad.mottak.delbestilling.DelbestillingStatus
import no.nav.hjelpemidler.soknad.mottak.metrics.Metrics
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
import java.util.Timer
import kotlin.concurrent.scheduleAtFixedRate
import kotlin.time.Duration.Companion.seconds

private val logger = KotlinLogging.logger {}

fun main() {
    val azureADClient = azureADClient(Apache.create()) {
        cache(leeway = 10.seconds)
    }

    val søknadForRiverClient = SøknadForRiverClient(
        Configuration.SOKNADSBEHANDLING_API_BASEURL,
        azureADClient.withScope(Configuration.SOKNADSBEHANDLING_API_SCOPE),
    )
    val infotrygdProxyClient = InfotrygdProxyClient(
        Configuration.INFOTRYGD_PROXY_API_BASEURL,
        azureADClient.withScope(Configuration.INFOTRYGD_PROXY_API_SCOPE),
    )
    val pdlClient = PdlClient(
        Configuration.PDL_GRAPHQL_URL,
        azureADClient.withScope(Configuration.PDL_GRAPHQL_SCOPE),
    )
    val delbestillingClient = DelbestillingClient(
        Configuration.DELBESTILLING_API_BASEURL,
        azureADClient.withScope(Configuration.DELBESTILLING_API_SCOPE),
    )

    RapidApplication.create(no.nav.hjelpemidler.configuration.Configuration.current)
        .apply {
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
        }
        .start()
}

private fun startSøknadUtgåttScheduling(søknadsgodkjenningService: SøknadsgodkjenningService) {
    val timer = Timer("utgatt-soknader-task", true)

    timer.scheduleAtFixedRate(60000, 1000 * 60 * 60) {
        runBlocking {
            launch {
                logger.info { "Markerer utgåtte søknader..." }
                val antallUtgåtte = søknadsgodkjenningService.slettUtgåtteSøknader()
                logger.info { "Antall utgåtte søknader: $antallUtgåtte" }
            }
        }
    }
}
