package no.nav.hjelpemidler.soknad.mottak.river

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.hjelpemidler.behovsmeldingsmodell.BehovsmeldingStatus
import no.nav.hjelpemidler.behovsmeldingsmodell.sak.InfotrygdSakId
import no.nav.hjelpemidler.behovsmeldingsmodell.sak.Sakstilknytning
import no.nav.hjelpemidler.logging.teamDebug
import no.nav.hjelpemidler.soknad.mottak.melding.OvervåkVedtaksresultatMelding
import no.nav.hjelpemidler.soknad.mottak.melding.SøknadUnderBehandlingMelding
import no.nav.hjelpemidler.soknad.mottak.soknadsbehandling.SøknadsbehandlingService
import java.util.UUID

private val log = KotlinLogging.logger {}

class DigitalSøknadEndeligJournalført(
    rapidsConnection: RapidsConnection,
    private val søknadsbehandlingService: SøknadsbehandlingService,
) : AsyncPacketListener {
    init {
        River(rapidsConnection).apply {
            precondition { it.requireValue("eventName", "DigitalSoeknadEndeligJournalfoert") }
            validate {
                it.requireKey(
                    "soknadId",
                    "fodselNrBruker",
                    "hendelse",
                    "hendelse.journalingEventSAF",
                    "hendelse.journalingEventSAF.sak",
                    "hendelse.journalingEventSAF.sak.fagsakId",
                )
            }
        }.register(this)
    }

    private val JsonMessage.søknadId get() = uuidValue("soknadId")
    private val JsonMessage.fnrBruker get() = this["fodselNrBruker"].textValue()
    private val JsonMessage.fagsakId get() = InfotrygdSakId(this["hendelse"]["journalingEventSAF"]["sak"]["fagsakId"].textValue())

    override suspend fun onPacketAsync(packet: JsonMessage, context: MessageContext) {
        val søknadId = packet.søknadId
        val fnrBruker = packet.fnrBruker
        val fagsakId = packet.fagsakId

        // fixme -> vi kan vel fjerne dette nå eller?
        if (packet.søknadId == UUID.fromString("1a404d1a-4cd9-4eb9-89f9-fa964230b8fe")) {
            log.info { "Tar søknadId: ${packet.søknadId} ut av køen og logger til sikkerlogg her" }
            val rawJson = packet.toJson()
            log.teamDebug { "JSON for søknad som ble skippet, søknadId: ${packet.søknadId}, json: $rawJson" }
            return
        }

        // fixme -> burde ikke de neste to operasjonene skje i samme transaksjon i backend?
        søknadsbehandlingService.oppdaterStatus(søknadId, BehovsmeldingStatus.ENDELIG_JOURNALFØRT)
        søknadsbehandlingService.lagreSakstilknytning(søknadId, Sakstilknytning.Infotrygd(fagsakId, fnrBruker))

        // På dette tidspunktet har det ikkje blitt gjort eit vedtak i Infotrygd, så vedtaksresultat og vedtaksdato er null
        context.publish(fnrBruker, OvervåkVedtaksresultatMelding(søknadId, fnrBruker, fagsakId))

        val behovsmeldingType = søknadsbehandlingService.hentBehovsmeldingstype(søknadId)

        // Melding til Ditt NAV
        context.publish(fnrBruker, SøknadUnderBehandlingMelding(søknadId, fnrBruker, behovsmeldingType))
        log.info {
            val type = behovsmeldingType.toString().lowercase()
            "Endelig journalført digital $type mottatt, den er lagret og det er gitt beskjed til hm-infotrygd-poller og hm-ditt-nav for søknadId: $søknadId"
        }
    }
}
