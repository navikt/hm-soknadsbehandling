package no.nav.hjelpemidler.soknad.mottak.river

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.hjelpemidler.behovsmeldingsmodell.BehovsmeldingStatus
import no.nav.hjelpemidler.behovsmeldingsmodell.BehovsmeldingType
import no.nav.hjelpemidler.behovsmeldingsmodell.sak.InfotrygdSakId
import no.nav.hjelpemidler.behovsmeldingsmodell.sak.Sakstilknytning
import no.nav.hjelpemidler.soknad.mottak.metrics.Metrics
import no.nav.hjelpemidler.soknad.mottak.service.PapirsøknadData
import no.nav.hjelpemidler.soknad.mottak.service.SøknadUnderBehandlingData
import no.nav.hjelpemidler.soknad.mottak.service.VedtaksresultatData
import no.nav.hjelpemidler.soknad.mottak.soknadsbehandling.SøknadsbehandlingService
import java.util.UUID

private val log = KotlinLogging.logger {}

class PapirsøknadEndeligJournalført(
    rapidsConnection: RapidsConnection,
    private val søknadsbehandlingService: SøknadsbehandlingService,
    private val metrics: Metrics,
) : AsyncPacketListener {
    init {
        River(rapidsConnection).apply {
            validate { it.demandValue("eventName", "PapirSoeknadEndeligJournalfoert") }
            validate {
                it.requireKey(
                    "fodselNrBruker",
                    "hendelse",
                    "hendelse.journalingEvent",
                    "hendelse.journalingEvent.journalpostId",
                    "eventId",
                    "hendelse.journalingEventSAF",
                    "hendelse.journalingEventSAF.sak",
                    "hendelse.journalingEventSAF.sak.fagsakId",
                    "hendelse.journalingEventSAF.avsenderMottaker",
                    "hendelse.journalingEventSAF.avsenderMottaker.navn"
                )
            }
        }.register(this)
    }

    private val JsonMessage.eventId get() = uuidValue("eventId")
    private val JsonMessage.fnrBruker get() = this["fodselNrBruker"].textValue()
    private val JsonMessage.journalpostId get() = this["hendelse"]["journalingEventSAF"]["journalpostId"].textValue()
    private val JsonMessage.fagsakId get() = InfotrygdSakId(this["hendelse"]["journalingEventSAF"]["sak"]["fagsakId"].textValue())
    private val JsonMessage.navnBruker get() = this["hendelse"]["journalingEventSAF"]["avsenderMottaker"]["navn"].textValue()

    override suspend fun onPacketAsync(packet: JsonMessage, context: MessageContext) {
        if (packet.eventId in skipList) {
            log.info { "Hopper over event i skipList: ${packet.eventId}" }
            return
        }

        val søknadId = UUID.randomUUID()
        val fnrBruker = packet.fnrBruker
        val fagsakId = packet.fagsakId

        val vedtaksresultatData = VedtaksresultatData(søknadId, fnrBruker, fagsakId)

        try {
            val søknad = PapirsøknadData(
                fnrBruker = fnrBruker,
                søknadId = søknadId,
                status = BehovsmeldingStatus.ENDELIG_JOURNALFØRT,
                journalpostId = packet.journalpostId,
                navnBruker = packet.navnBruker
            )

            // fixme -> kunne vi ikke bare sjekket dette i backend?
            if (søknadsbehandlingService.fnrOgJournalpostIdFinnes(søknad.fnrBruker, søknad.journalpostId)) {
                log.warn { "En søknad med dette fødselsnummeret og journalpostId-en er allerede lagret i databasen: $søknadId, journalpostId: ${søknad.journalpostId}, eventId: ${packet.eventId}" }
                return
            }

            // fixme -> kunne vi ikke gjort de neste to kallene i én transaksjon?
            søknadsbehandlingService.lagreBehovsmelding(søknad)
            søknadsbehandlingService.lagreSakstilknytning(søknadId, Sakstilknytning.Infotrygd(fagsakId, fnrBruker))

            context.publish(fnrBruker, vedtaksresultatData.toJson("hm-InfotrygdAddToPollVedtakList"))
            log.info { "Papirsøknad mottatt og lagret: $søknadId" }

            // Send melding til Ditt NAV
            context.publish(
                fnrBruker,
                SøknadUnderBehandlingData(
                    søknadId,
                    fnrBruker,
                    BehovsmeldingType.SØKNAD
                ).toJson("hm-SøknadUnderBehandling")
            )
            log.info { "Endelig journalført: Papirsøknad mottatt, lagret, og beskjed til Infotrygd-poller og hm-ditt-nav sendt for søknadId: $søknadId" }

            metrics.papirsøknad(packet.fnrBruker)
        } catch (e: Exception) {
            log.error(e) { "Håndtering av eventId: ${packet.eventId} feilet" }
            throw e
        }
    }

    private val skipList = listOf<UUID>()
}
