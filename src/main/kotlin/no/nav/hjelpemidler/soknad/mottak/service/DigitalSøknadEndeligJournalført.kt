package no.nav.hjelpemidler.soknad.mottak.service

import mu.KotlinLogging
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.hjelpemidler.soknad.mottak.db.InfotrygdStore
import no.nav.hjelpemidler.soknad.mottak.db.SøknadStore
import no.nav.hjelpemidler.soknad.mottak.metrics.Prometheus
import no.nav.hjelpemidler.soknad.mottak.service.VedtaksresultatData.Companion.getSaksblokkFromFagsakId
import no.nav.hjelpemidler.soknad.mottak.service.VedtaksresultatData.Companion.getSaksnrFromFagsakId
import no.nav.hjelpemidler.soknad.mottak.service.VedtaksresultatData.Companion.getTrygdekontorNrFromFagsakId
import java.util.UUID

private val logger = KotlinLogging.logger {}

internal class DigitalSøknadEndeligJournalført(rapidsConnection: RapidsConnection, private val store: SøknadStore, private val infotrygdStore: InfotrygdStore) :
    River.PacketListener {

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

    private val JsonMessage.søknadId get() = this["soknadId"].textValue()
    private val JsonMessage.fnrBruker get() = this["fodselNrBruker"].textValue()
    private val JsonMessage.fagsakId get() = this["hendelse"]["journalingEventSAF"]["sak"]["fagsakId"].textValue()

    override fun onPacket(packet: JsonMessage, context: RapidsConnection.MessageContext) {
        val søknadId = UUID.fromString(packet.søknadId)
        val fnrBruker = packet.fnrBruker
        val fagsakId = packet.fagsakId

        // På dette tidspunktet har det ikkje blitt gjort eit vedtak i Infotrygd, så resultat og vedtaksdato er null
        val vedtaksresultatData = VedtaksresultatData(
            søknadId,
            fnrBruker,
            getTrygdekontorNrFromFagsakId(fagsakId),
            getSaksblokkFromFagsakId(fagsakId),
            getSaksnrFromFagsakId(fagsakId),
        )

        // TODO: Sjå på kombinasjonen av feilhandtering her
        oppdaterStatus(søknadId)
        opprettKnytningMellomFagsakOgSøknad(vedtaksresultatData, fagsakId)

        // TODO: Forward melding til hm-infotrygd-poller
        context.send(fnrBruker, vedtaksresultatData.toJson("hm-InfotrygdAddToPollVedtakList"))
    }

    private fun oppdaterStatus(søknadId: UUID) =
        kotlin.runCatching {
            store.oppdaterStatus(søknadId, Status.ENDELIG_JOURNALFØRT)
        }.onSuccess {
            if (it > 0) {
                logger.info("Søknad updated to endelig journalført: $søknadId, it=$it")
            } else {
                logger.error("Ingen søknader oppdatert ved endelig journalførtevent: $søknadId")
            }
        }.onFailure {
            logger.error("Failed to update søknad to endelig journalført: $søknadId")
        }.getOrThrow()

    private fun opprettKnytningMellomFagsakOgSøknad(vedtaksresultatData: VedtaksresultatData, fagsakId: String) =
        kotlin.runCatching {
            infotrygdStore.lagKnytningMellomFagsakOgSøknad(vedtaksresultatData)
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
                    logger.warn("Fleire knytningar laga mellom søknadId ${vedtaksresultatData.søknadId} og Infotrygd sin fagsakId $fagsakId")
                    Prometheus.knytningMellomSøknadOgInfotrygdProblemCounter.inc()
                }
            }
        }.onFailure {
            logger.error(it) { "Feila med å lage knytning mellom søknadId ${vedtaksresultatData.søknadId} og fagsakId $fagsakId" }
        }.getOrThrow()
}
