package no.nav.hjelpemidler.soknad.mottak

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.hjelpemidler.configuration.Environment
import no.nav.hjelpemidler.domain.person.TILLAT_SYNTETISKE_FØDSELSNUMRE
import no.nav.hjelpemidler.soknad.mottak.client.DelbestillingClient
import no.nav.hjelpemidler.soknad.mottak.client.GodkjenningskursClient
import no.nav.hjelpemidler.soknad.mottak.client.InfotrygdProxyClient
import no.nav.hjelpemidler.soknad.mottak.client.PdlClient
import no.nav.hjelpemidler.soknad.mottak.client.SøknadApiClient
import no.nav.hjelpemidler.soknad.mottak.client.SøknadsbehandlingClient
import no.nav.hjelpemidler.soknad.mottak.delbestilling.DelbestillingOrdrelinjeStatus
import no.nav.hjelpemidler.soknad.mottak.delbestilling.DelbestillingStatus
import no.nav.hjelpemidler.soknad.mottak.godkjenningskurs.GodkjenningskursService
import no.nav.hjelpemidler.soknad.mottak.metrics.Metrics
import no.nav.hjelpemidler.soknad.mottak.river.BehovsmeldingAvventerPdfDataSink
import no.nav.hjelpemidler.soknad.mottak.river.BehovsmeldingIkkeBehovForBrukerbekreftelseDataSink
import no.nav.hjelpemidler.soknad.mottak.river.BehovsmeldingTilBrukerbekreftelseDataSink
import no.nav.hjelpemidler.soknad.mottak.river.BestillingAvvistFraHotsak
import no.nav.hjelpemidler.soknad.mottak.river.BestillingGodkjentFraHotsak
import no.nav.hjelpemidler.soknad.mottak.river.DigitalSøknadAutomatiskJournalført
import no.nav.hjelpemidler.soknad.mottak.river.DigitalSøknadEndeligJournalført
import no.nav.hjelpemidler.soknad.mottak.river.DigitalSøknadEndeligJournalførtEtterOverføring
import no.nav.hjelpemidler.soknad.mottak.river.GodkjennSøknad
import no.nav.hjelpemidler.soknad.mottak.river.HotsakHenlagt
import no.nav.hjelpemidler.soknad.mottak.river.HotsakOpprettet
import no.nav.hjelpemidler.soknad.mottak.river.JournalpostJournalført
import no.nav.hjelpemidler.soknad.mottak.river.JournalpostSink
import no.nav.hjelpemidler.soknad.mottak.river.NyHotsakOrdrelinje
import no.nav.hjelpemidler.soknad.mottak.river.NyInfotrygdOrdrelinje
import no.nav.hjelpemidler.soknad.mottak.river.OppgaveSink
import no.nav.hjelpemidler.soknad.mottak.river.PapirsøknadEndeligJournalført
import no.nav.hjelpemidler.soknad.mottak.river.SlettSøknad
import no.nav.hjelpemidler.soknad.mottak.river.VedtaksresultatFraHotsak
import no.nav.hjelpemidler.soknad.mottak.river.VedtaksresultatFraInfotrygd
import no.nav.hjelpemidler.soknad.mottak.soknadapi.SøknadApiService
import no.nav.hjelpemidler.soknad.mottak.soknadsbehandling.SøknadsbehandlingService
import no.nav.hjelpemidler.soknad.mottak.soknadsbehandling.SøknadsgodkjenningService
import java.util.Timer
import kotlin.concurrent.scheduleAtFixedRate

private val log = KotlinLogging.logger {}

fun main() {

    TILLAT_SYNTETISKE_FØDSELSNUMRE = !Environment.current.isProd

    val søknadsbehandlingClient = SøknadsbehandlingClient()
    val infotrygdProxyClient = InfotrygdProxyClient()
    val pdlClient = PdlClient()
    val delbestillingClient = DelbestillingClient()
    val søknadApiClient = SøknadApiClient()
    val godkjenningskursClient = GodkjenningskursClient()

    val søknadsbehandlingService = SøknadsbehandlingService(søknadsbehandlingClient)
    val søknadApiService = SøknadApiService(søknadApiClient)
    val godkjenningskursService = GodkjenningskursService(godkjenningskursClient)

    RapidApplication.create(no.nav.hjelpemidler.configuration.Configuration)
        .apply {
            val metrics = Metrics(this, pdlClient)

            startSøknadUtgåttScheduling(SøknadsgodkjenningService(this, søknadsbehandlingClient))

            BehovsmeldingIkkeBehovForBrukerbekreftelseDataSink(
                this,
                søknadsbehandlingService,
                godkjenningskursService,
                metrics
            )
            BehovsmeldingTilBrukerbekreftelseDataSink(this, søknadsbehandlingService, godkjenningskursService, metrics)
            BehovsmeldingAvventerPdfDataSink(this, søknadsbehandlingService, søknadApiService)

            BestillingAvvistFraHotsak(this, søknadsbehandlingService)
            BestillingGodkjentFraHotsak(this, søknadsbehandlingService)

            DigitalSøknadAutomatiskJournalført(this, søknadsbehandlingService)
            DigitalSøknadEndeligJournalført(this, søknadsbehandlingService)
            DigitalSøknadEndeligJournalførtEtterOverføring(this, søknadsbehandlingService)
            PapirsøknadEndeligJournalført(this, søknadsbehandlingService, metrics)

            GodkjennSøknad(this, søknadsbehandlingService)

            HotsakOpprettet(this, søknadsbehandlingService)
            HotsakHenlagt(this, søknadsbehandlingService)

            if (Environment.current.isDev) {
                JournalpostJournalført(this, søknadsbehandlingService)
            }

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
