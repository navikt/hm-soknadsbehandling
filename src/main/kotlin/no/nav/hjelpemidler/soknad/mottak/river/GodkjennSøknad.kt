package no.nav.hjelpemidler.soknad.mottak.river

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.hjelpemidler.behovsmeldingsmodell.BehovsmeldingStatus
import no.nav.hjelpemidler.behovsmeldingsmodell.SøknadDto
import no.nav.hjelpemidler.logging.secureLog
import no.nav.hjelpemidler.soknad.mottak.melding.BehovsmeldingMottattMelding
import no.nav.hjelpemidler.soknad.mottak.metrics.Prometheus
import no.nav.hjelpemidler.soknad.mottak.soknadsbehandling.SøknadsbehandlingService
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId

private val log = KotlinLogging.logger {}

class GodkjennSøknad(
    rapidsConnection: RapidsConnection,
    private val søknadsbehandlingService: SøknadsbehandlingService,
) : AsyncPacketListener {
    init {
        River(rapidsConnection).apply {
            precondition { it.requireValue("eventName", "godkjentAvBruker") }
            validate { it.requireKey("soknadId") }
        }.register(this)
    }

    private val JsonMessage.søknadId get() = uuidValue("soknadId")

    override suspend fun onPacketAsync(packet: JsonMessage, context: MessageContext) {
        val søknadId = packet.søknadId
        log.info { "Bruker har godkjent søknad: $søknadId" }
        // fixme -> dette burde orkestreres i backend med e.g. POST /soknad/{soknadId}/godkjenning
        try {
            val søknad = søknadsbehandlingService.hentSøknad(søknadId)
            if (søknad.status != BehovsmeldingStatus.VENTER_GODKJENNING) {
                log.info { "Søknad til godkjenning har ikke status VENTER_GODKJENNING, søknadId: $søknadId" }
            } else {
                loggTidBruktForGodkjenning(søknad)
                søknadsbehandlingService.oppdaterStatus(søknadId, BehovsmeldingStatus.GODKJENT)
                val oppdatertSøknad = søknadsbehandlingService.hentSøknad(søknadId, true)
                val fnrBruker = oppdatertSøknad.fnrBruker
                context.publish(
                    fnrBruker,
                    BehovsmeldingMottattMelding("hm-søknadGodkjentAvBrukerMottatt", oppdatertSøknad)
                )
                Prometheus.søknadGodkjentAvBrukerCounter.increment()
                log.info { "Søknad er godkjent av bruker, søknadId: $søknadId" }
                secureLog.info { "Søknad er godkjent av bruker, søknadId: $søknadId, fnrBruker: $fnrBruker" }
            }
        } catch (e: Exception) {
            log.error(e) { "Håndtering av brukergodkjenning for søknadId: $søknadId feilet" }
            throw e
        }
    }

    private fun loggTidBruktForGodkjenning(søknad: SøknadDto) {
        val duration = Duration.between(
            LocalDateTime.ofInstant(søknad.søknadOpprettet, ZoneId.systemDefault()),
            LocalDateTime.now()
        )
        val tid =
            "${duration.toDaysPart()} dager, ${duration.toHoursPart()} timer, ${duration.toMinutesPart()} minutter, ${duration.toSecondsPart()} sekunder"
        log.info { "Tid brukt fra opprettelse til godkjenning av søknad med søknadId: ${søknad.søknadId} var: $tid" }
    }
}
