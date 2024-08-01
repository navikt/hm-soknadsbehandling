package no.nav.hjelpemidler.soknad.mottak.river

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.hjelpemidler.behovsmeldingsmodell.BehovsmeldingStatus
import no.nav.hjelpemidler.behovsmeldingsmodell.sak.InfotrygdSakId
import no.nav.hjelpemidler.behovsmeldingsmodell.sak.Sakstilknytning
import no.nav.hjelpemidler.soknad.mottak.logging.sikkerlogg
import no.nav.hjelpemidler.soknad.mottak.service.SøknadUnderBehandlingData
import no.nav.hjelpemidler.soknad.mottak.service.VedtaksresultatData
import no.nav.hjelpemidler.soknad.mottak.soknadsbehandling.SøknadsbehandlingService
import java.util.UUID

private val logger = KotlinLogging.logger {}

class DigitalSøknadEndeligJournalført(
    rapidsConnection: RapidsConnection,
    private val søknadsbehandlingService: SøknadsbehandlingService,
) : AsyncPacketListener {
    init {
        River(rapidsConnection).apply {
            validate { it.demandValue("eventName", "DigitalSoeknadEndeligJournalfoert") }
            validate { it.requireKey("soknadId", "fodselNrBruker") }
            validate { it.requireKey("hendelse") }
            validate { it.requireKey("hendelse.journalingEventSAF") }
            validate { it.requireKey("hendelse.journalingEventSAF.sak") }
            validate { it.requireKey("hendelse.journalingEventSAF.sak.fagsakId") }
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
            logger.info { "Tar søknadId: ${packet.søknadId} ut av køen og logger til sikkerlogg her" }
            val rawJson = packet.toJson()
            sikkerlogg.info { "DEBUG DEBUG: rawJson for skipped søknad, søknadId: ${packet.søknadId}, rawJson: $rawJson" }
            return
        }

        // fixme -> burde ikke de neste to operasjonene skje i samme transaksjon i backend?
        søknadsbehandlingService.oppdaterStatus(søknadId, BehovsmeldingStatus.ENDELIG_JOURNALFØRT)
        søknadsbehandlingService.lagreSakstilknytning(søknadId, Sakstilknytning.Infotrygd(fagsakId, fnrBruker))

        // På dette tidspunktet har det ikkje blitt gjort eit vedtak i Infotrygd, så vedtaksresultat og vedtaksdato er null
        val vedtaksresultatData = VedtaksresultatData(søknadId, fnrBruker, fagsakId)

        context.publish(fnrBruker, vedtaksresultatData.toJson("hm-InfotrygdAddToPollVedtakList"))

        val behovsmeldingType = søknadsbehandlingService.hentBehovsmeldingstype(søknadId)

        // Melding til Ditt NAV
        context.publish(
            fnrBruker,
            SøknadUnderBehandlingData(søknadId, fnrBruker, behovsmeldingType).toJson("hm-SøknadUnderBehandling"),
        )
        logger.info {
            "Endelig journalført: Digital ${
                behovsmeldingType.toString().lowercase()
            } mottatt, lagret, og beskjed til Infotrygd-poller og hm-ditt-nav sendt for søknadId: $søknadId"
        }
    }
}
