package no.nav.hjelpemidler.soknad.mottak.river

import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
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
private val sikkerlogg = KotlinLogging.logger("tjenestekall")

internal class PapirSøknadEndeligJournalført(
    rapidsConnection: RapidsConnection,
    private val søknadForRiverClient: SøknadForRiverClient,
    private val metrics: Metrics
) : PacketListenerWithOnError {
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

    private val JsonMessage.eventId get() = this["eventId"].textValue()
    private val JsonMessage.fnrBruker get() = this["fodselNrBruker"].textValue()
    private val JsonMessage.journalpostId get() = this["hendelse"]["journalingEvent"]["journalpostId"].asInt()
    private val JsonMessage.fagsakId get() = this["hendelse"]["journalingEventSAF"]["sak"]["fagsakId"].textValue()
    private val JsonMessage.navnBruker get() = this["hendelse"]["journalingEventSAF"]["avsenderMottaker"]["navn"].textValue()

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        runBlocking {

            if (skipEvent(UUID.fromString(packet.eventId))) {
                logger.info { "Hopper over event i skip-list: ${packet.eventId}" }
                return@runBlocking
            }

            val soknadId = UUID.randomUUID()
            val fnrBruker = packet.fnrBruker
            val fagsakId = packet.fagsakId

            val vedtaksresultatData = VedtaksresultatData(
                soknadId,
                fnrBruker,
                VedtaksresultatData.getTrygdekontorNrFromFagsakId(fagsakId),
                VedtaksresultatData.getSaksblokkFromFagsakId(fagsakId),
                VedtaksresultatData.getSaksnrFromFagsakId(fagsakId),
            )

            try {
                val soknadData = PapirSøknadData(
                    fnrBruker = fnrBruker,
                    soknadId = soknadId,
                    status = Status.ENDELIG_JOURNALFØRT,
                    journalpostid = packet.journalpostId,
                    navnBruker = packet.navnBruker
                )

                if (søknadForRiverClient.fnrOgJournalpostIdFinnes(soknadData.fnrBruker, soknadData.journalpostid)) {
                    logger.warn { "En søknad med dette fødselsnummeret og journalpostIden er allerede lagret i databasen: $soknadId, journalpostId: ${soknadData.journalpostid}, eventId: ${packet.eventId}" }
                    return@runBlocking
                }

                save(soknadData)
                opprettKnytningMellomFagsakOgSøknad(fagsakId = fagsakId, vedtaksresultatData = vedtaksresultatData)
                context.publish(fnrBruker, vedtaksresultatData.toJson("hm-InfotrygdAddToPollVedtakList"))
                logger.info { "Papirsøknad mottatt og lagret: $soknadId" }

                // Send melding til Ditt NAV
                context.publish(
                    fnrBruker,
                    SøknadUnderBehandlingData(soknadId, fnrBruker, BehovsmeldingType.SØKNAD).toJson("hm-SøknadUnderBehandling")
                )
                logger.info { "Endelig journalført: Papirsøknad mottatt, lagret, og beskjed til Infotrygd-poller og hm-ditt-nav sendt for søknadId: $soknadId" }

                metrics.papirSoknad(packet.fnrBruker)
            } catch (e: Exception) {
                throw RuntimeException("Håndtering av event ${packet.eventId} feilet", e)
            }
        }
    }

    private fun skipEvent(eventId: UUID): Boolean {
        val skipList = mutableListOf<UUID>()
        return skipList.any { it == eventId }
    }

    private suspend fun save(soknadData: PapirSøknadData) =
        kotlin.runCatching {
            søknadForRiverClient.savePapir(soknadData)
        }.onSuccess {
            if (it > 0) {
                logger.info("Endelig journalført papirsøknad saved: ${soknadData.soknadId} it=$it")
            } else {
                logger.error("Lagring av papirsøknad feilet. Ingen rader påvirket under lagring.")
            }
        }.onFailure {
            logger.error(it) { "Failed to save papirsøknad klar til godkjenning: ${soknadData.soknadId}" }
        }.getOrThrow()

    private suspend fun opprettKnytningMellomFagsakOgSøknad(
        vedtaksresultatData: VedtaksresultatData,
        fagsakId: String
    ) =
        kotlin.runCatching {
            søknadForRiverClient.lagKnytningMellomFagsakOgSøknad(vedtaksresultatData)
        }.onSuccess {
            when (it) {
                0 -> {
                    logger.warn("Inga knytning laga mellom søknadId ${vedtaksresultatData.søknadId} og Infotrygd sin fagsakId $fagsakId")
                    Prometheus.knytningMellomSøknadOgInfotrygdProblemCounter.inc()
                }
                1 -> {
                    logger.info("Knytning lagra mellom søknadId ${vedtaksresultatData.søknadId} og Infotrygd sin fagsakId $fagsakId")
                    Prometheus.knytningMellomSøknadOgInfotrygdOpprettaCounter.inc()
                }
                else -> {
                    logger.error("Fleire knytningar laga mellom søknadId ${vedtaksresultatData.søknadId} og Infotrygd sin fagsakId $fagsakId")
                    Prometheus.knytningMellomSøknadOgInfotrygdProblemCounter.inc()
                }
            }
        }.onFailure {
            logger.error(it) { "Feila med å lage knytning mellom søknadId ${vedtaksresultatData.søknadId} og fagsakId $fagsakId" }
        }.getOrThrow()
}
