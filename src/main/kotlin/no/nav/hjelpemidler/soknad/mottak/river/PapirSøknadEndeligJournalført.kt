package no.nav.hjelpemidler.soknad.mottak.river

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.hjelpemidler.behovsmeldingsmodell.SøknadId
import no.nav.hjelpemidler.behovsmeldingsmodell.sak.InfotrygdSakId
import no.nav.hjelpemidler.behovsmeldingsmodell.sak.Sakstilknytning
import no.nav.hjelpemidler.soknad.mottak.client.SøknadForRiverClient
import no.nav.hjelpemidler.soknad.mottak.metrics.Metrics
import no.nav.hjelpemidler.soknad.mottak.metrics.Prometheus
import no.nav.hjelpemidler.soknad.mottak.service.BehovsmeldingType
import no.nav.hjelpemidler.soknad.mottak.service.PapirSøknadData
import no.nav.hjelpemidler.soknad.mottak.service.Status
import no.nav.hjelpemidler.soknad.mottak.service.SøknadUnderBehandlingData
import no.nav.hjelpemidler.soknad.mottak.service.VedtaksresultatData
import java.util.UUID

private val logger = KotlinLogging.logger {}

class PapirSøknadEndeligJournalført(
    rapidsConnection: RapidsConnection,
    private val søknadForRiverClient: SøknadForRiverClient,
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
    private val JsonMessage.journalpostId get() = this["hendelse"]["journalingEvent"]["journalpostId"].asInt()
    private val JsonMessage.fagsakId get() = InfotrygdSakId(this["hendelse"]["journalingEventSAF"]["sak"]["fagsakId"].textValue())
    private val JsonMessage.navnBruker get() = this["hendelse"]["journalingEventSAF"]["avsenderMottaker"]["navn"].textValue()

    override suspend fun onPacketAsync(packet: JsonMessage, context: MessageContext) {
        if (skipEvent(packet.eventId)) {
            logger.info { "Hopper over event i skip-list: ${packet.eventId}" }
            return
        }

        val søknadId = UUID.randomUUID()
        val fnrBruker = packet.fnrBruker
        val fagsakId = packet.fagsakId

        val vedtaksresultatData = VedtaksresultatData(søknadId, fnrBruker, fagsakId)

        try {
            val soknadData = PapirSøknadData(
                fnrBruker = fnrBruker,
                soknadId = søknadId,
                status = Status.ENDELIG_JOURNALFØRT,
                journalpostid = packet.journalpostId,
                navnBruker = packet.navnBruker
            )

            if (søknadForRiverClient.fnrOgJournalpostIdFinnes(soknadData.fnrBruker, soknadData.journalpostid)) {
                logger.warn { "En søknad med dette fødselsnummeret og journalpostId-en er allerede lagret i databasen: $søknadId, journalpostId: ${soknadData.journalpostid}, eventId: ${packet.eventId}" }
                return
            }

            // fixme -> kunne vi ikke gjort de neste to kallene i én transaksjon?
            save(soknadData)
            opprettKnytningMellomFagsakOgSøknad(søknadId, fagsakId, fnrBruker)
            context.publish(fnrBruker, vedtaksresultatData.toJson("hm-InfotrygdAddToPollVedtakList"))
            logger.info { "Papirsøknad mottatt og lagret: $søknadId" }

            // Send melding til Ditt NAV
            context.publish(
                fnrBruker,
                SøknadUnderBehandlingData(
                    søknadId,
                    fnrBruker,
                    BehovsmeldingType.SØKNAD
                ).toJson("hm-SøknadUnderBehandling")
            )
            logger.info { "Endelig journalført: Papirsøknad mottatt, lagret, og beskjed til Infotrygd-poller og hm-ditt-nav sendt for søknadId: $søknadId" }

            metrics.papirSoknad(packet.fnrBruker)
        } catch (e: Exception) {
            throw RuntimeException("Håndtering av event ${packet.eventId} feilet", e)
        }
    }

    private fun skipEvent(eventId: UUID): Boolean {
        val skipList = mutableListOf<UUID>()
        return skipList.any { it == eventId }
    }

    private suspend fun save(soknadData: PapirSøknadData) =
        runCatching {
            søknadForRiverClient.lagrePapirsøknad(soknadData)
        }.onSuccess {
            if (it > 0) {
                logger.info { "Endelig journalført papirsøknad lagret: ${soknadData.soknadId}, it: $it" }
            } else {
                logger.error { "Lagring av papirsøknad feilet. Ingen rader påvirket under lagring." }
            }
        }.onFailure {
            logger.error(it) { "Failed to save papirsøknad klar til godkjenning, søknadId: ${soknadData.soknadId}" }
        }.getOrThrow()

    private suspend fun opprettKnytningMellomFagsakOgSøknad(
        søknadId: SøknadId,
        fagsakId: InfotrygdSakId,
        fnrBruker: String,
    ) =
        runCatching {
            søknadForRiverClient.lagreSakstilknytning(søknadId, Sakstilknytning.Infotrygd(fagsakId, fnrBruker))
        }.onSuccess {
            when (it) {
                0 -> {
                    logger.warn { "Inga knytning laga mellom søknadId: $søknadId og Infotrygd sin fagsakId: $fagsakId" }
                    Prometheus.knytningMellomSøknadOgInfotrygdProblemCounter.inc()
                }

                1 -> {
                    logger.info { "Knytning lagra mellom søknadId: $søknadId og Infotrygd sin fagsakId: $fagsakId" }
                    Prometheus.knytningMellomSøknadOgInfotrygdOpprettaCounter.inc()
                }

                else -> {
                    logger.error { "Fleire knytningar laga mellom søknadId: $søknadId og Infotrygd sin fagsakId: $fagsakId" }
                    Prometheus.knytningMellomSøknadOgInfotrygdProblemCounter.inc()
                }
            }
        }.onFailure {
            logger.error(it) { "Feila med å lage knytning mellom søknadId: $søknadId og fagsakId: $fagsakId" }
        }.getOrThrow()
}
