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
import no.nav.hjelpemidler.soknad.mottak.metrics.Prometheus
import no.nav.hjelpemidler.soknad.mottak.service.Status
import no.nav.hjelpemidler.soknad.mottak.service.VedtaksresultatData
import java.util.UUID

private val logger = KotlinLogging.logger {}

class DigitalSøknadEndeligJournalførtEtterTilbakeføring(
    rapidsConnection: RapidsConnection,
    private val søknadForRiverClient: SøknadForRiverClient,
) : AsyncPacketListener {
    init {
        River(rapidsConnection).apply {
            validate { it.demandValue("eventName", "DigitalSoeknadEndeligJournalfoertEtterTilbakefoering") }
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

        // På dette tidspunktet har det ikkje blitt gjort eit vedtak i Infotrygd, så resultat og vedtaksdato er null
        val vedtaksresultatData = VedtaksresultatData(søknadId, fnrBruker, fagsakId)

        // fixme -> burde ikke dette skjedd samtidig med at vi lagrer sakstilknytningen?
        oppdaterStatus(søknadId)
        opprettKnytningMellomFagsakOgSøknad(søknadId, fagsakId, fnrBruker)
        context.publish(fnrBruker, vedtaksresultatData.toJson("hm-InfotrygdAddToPollVedtakList"))

        logger.info { "Endelig journalført: Digital søknad mottatt, lagret, og beskjed til Infotrygd-poller og hm-ditt-nav sendt for søknadId: $søknadId" }
    }

    private suspend fun oppdaterStatus(søknadId: UUID) =
        runCatching {
            søknadForRiverClient.oppdaterStatus(søknadId, Status.ENDELIG_JOURNALFØRT)
        }.onSuccess {
            if (it > 0) {
                logger.info { "Status på søknad satt til endelig journalført, søknadId: $søknadId, it: $it" }
            } else {
                logger.warn { "Status er allerede satt til endelig journalført, søknadId: $søknadId" }
            }
        }.onFailure {
            logger.error(it) { "Failed to update søknad to endelig journalført, søknadId: $søknadId" }
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
