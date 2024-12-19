package no.nav.hjelpemidler.soknad.mottak

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.engine.apache.Apache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.hjelpemidler.configuration.Environment
import no.nav.hjelpemidler.domain.person.TILLAT_SYNTETISKE_FØDSELSNUMRE
import no.nav.hjelpemidler.http.openid.azureADClient
import no.nav.hjelpemidler.soknad.mottak.client.InfotrygdProxyClient
import no.nav.hjelpemidler.soknad.mottak.client.PdlClient
import no.nav.hjelpemidler.soknad.mottak.client.SøknadsbehandlingClient
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
import no.nav.hjelpemidler.soknad.mottak.river.GodkjennSøknad
import no.nav.hjelpemidler.soknad.mottak.river.HotsakHenlagt
import no.nav.hjelpemidler.soknad.mottak.river.HotsakOpprettet
import no.nav.hjelpemidler.soknad.mottak.river.JournalpostSink
import no.nav.hjelpemidler.soknad.mottak.river.NyHotsakOrdrelinje
import no.nav.hjelpemidler.soknad.mottak.river.NyInfotrygdOrdrelinje
import no.nav.hjelpemidler.soknad.mottak.river.OppgaveSink
import no.nav.hjelpemidler.soknad.mottak.river.PapirsøknadEndeligJournalført
import no.nav.hjelpemidler.soknad.mottak.river.SlettSøknad
import no.nav.hjelpemidler.soknad.mottak.river.VedtaksresultatFraHotsak
import no.nav.hjelpemidler.soknad.mottak.river.VedtaksresultatFraInfotrygd
import no.nav.hjelpemidler.soknad.mottak.soknadsbehandling.SøknadsbehandlingService
import no.nav.hjelpemidler.soknad.mottak.soknadsbehandling.SøknadsgodkjenningService
import java.util.Timer
import kotlin.concurrent.scheduleAtFixedRate
import kotlin.time.Duration.Companion.seconds

private val log = KotlinLogging.logger {}

fun main() {

    TILLAT_SYNTETISKE_FØDSELSNUMRE = !Environment.current.isProd

    val azureADClient = azureADClient(Apache.create()) {
        cache(leeway = 10.seconds)
    }

    val søknadsbehandlingClient = SøknadsbehandlingClient(
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

    val søknadsbehandlingService = SøknadsbehandlingService(søknadsbehandlingClient)

    RapidApplication.create(no.nav.hjelpemidler.configuration.Configuration.current)
        .apply {
            val metrics = Metrics(this, pdlClient)

            startSøknadUtgåttScheduling(SøknadsgodkjenningService(this, søknadsbehandlingClient))

            BehovsmeldingIkkeBehovForBrukerbekreftelseDataSink(this, søknadsbehandlingService, metrics)
            BehovsmeldingTilBrukerbekreftelseDataSink(this, søknadsbehandlingService, metrics)

            BestillingAvvistFraHotsak(this, søknadsbehandlingService)
            BestillingFerdigstiltFraHotsak(this, søknadsbehandlingService)

            DigitalSøknadAutomatiskJournalført(this, søknadsbehandlingService)
            DigitalSøknadEndeligJournalført(this, søknadsbehandlingService)
            DigitalSøknadEndeligJournalførtEtterTilbakeføring(this, søknadsbehandlingService)
            PapirsøknadEndeligJournalført(this, søknadsbehandlingService, metrics)

            GodkjennSøknad(this, søknadsbehandlingService)

            HotsakOpprettet(this, søknadsbehandlingService)
            HotsakHenlagt(this, søknadsbehandlingService)

            JournalpostSink(this, søknadsbehandlingClient)
            OppgaveSink(this, søknadsbehandlingClient)

            NyHotsakOrdrelinje(this, søknadsbehandlingService)
            NyInfotrygdOrdrelinje(this, søknadsbehandlingService, infotrygdProxyClient)

            SlettSøknad(this, søknadsbehandlingService)

            VedtaksresultatFraHotsak(this, søknadsbehandlingService)
            VedtaksresultatFraInfotrygd(this, søknadsbehandlingService, metrics)

            // Delbestilling
            DelbestillingStatus(this, delbestillingClient)
            DelbestillingOrdrelinjeStatus(this, delbestillingClient)
        }
        .start()
}

private fun startSøknadUtgåttScheduling(søknadsgodkjenningService: SøknadsgodkjenningService) {
    val timer = Timer("utgatt-soknader-task", true)

    timer.scheduleAtFixedRate(60000, 1000 * 60 * 60) {
        runBlocking(Dispatchers.IO) {
            launch {
                log.info { "Markerer utgåtte søknader..." }
                val antallUtgåtte = søknadsgodkjenningService.slettUtgåtteSøknader()
                log.info { "Antall utgåtte søknader: $antallUtgåtte" }
            }
        }
    }
}
