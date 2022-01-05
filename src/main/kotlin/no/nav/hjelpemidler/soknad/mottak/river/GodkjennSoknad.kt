package no.nav.hjelpemidler.soknad.mottak.river

import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.hjelpemidler.soknad.mottak.client.SøknadForRiverClient
import no.nav.hjelpemidler.soknad.mottak.metrics.Prometheus
import no.nav.hjelpemidler.soknad.mottak.service.SoknadData
import no.nav.hjelpemidler.soknad.mottak.service.Status
import no.nav.hjelpemidler.soknad.mottak.service.periodeMellomDatoer
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

private val logger = KotlinLogging.logger {}
private val sikkerlogg = KotlinLogging.logger("tjenestekall")

internal class GodkjennSoknad(
    rapidsConnection: RapidsConnection,
    private val søknadForRiverClient: SøknadForRiverClient
) : PacketListenerWithOnError {

    init {
        River(rapidsConnection).apply {
            validate { it.demandValue("eventName", "godkjentAvBruker") }
            validate { it.requireKey("soknadId") }
        }.register(this)
    }

    private val JsonMessage.soknadId get() = this["soknadId"].textValue()

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        runBlocking {
            try {
                logger.info { "Bruker har godkjent søknad: ${packet.soknadId}" }
                val soknad = hentSoknadData(UUID.fromString(packet.soknadId))
                if (soknad.status != Status.VENTER_GODKJENNING) {
                    logger.info { "Søknad til godkjenning har ikke status VENTER_GODKJENNING. søknadId: ${packet.soknadId}" }
                } else {
                    loggTidBruktForGodkjenning(soknad)
                    update(UUID.fromString(packet.soknadId), Status.GODKJENT)
                    val oppdatertSoknad = hentSoknadData(UUID.fromString(packet.soknadId))
                    forward(oppdatertSoknad, context)
                }
            } catch (e: Exception) {
                logger.error(e) { "Håndtering av brukergodkjenning for søknad ${packet.soknadId} feilet" }
                throw e
            }
        }
    }

    private suspend fun update(soknadId: UUID, status: Status) =
        kotlin.runCatching {
            søknadForRiverClient.oppdaterStatus(soknadId, status)
        }.onSuccess {
            logger.info("Søknad $soknadId oppdatert med status $status")
        }.onFailure {
            logger.error(it) { "Failed to update søknad $soknadId med status $status" }
        }.getOrThrow()

    private suspend fun hentSoknadData(soknadId: UUID): SoknadData =
        kotlin.runCatching {
            søknadForRiverClient.hentSoknadData(soknadId)!!
        }.onFailure {
            logger.error(it) { "Failed to retrieve søknad $soknadId" }
        }.getOrThrow()

    private suspend fun loggTidBruktForGodkjenning(soknadData: SoknadData) {
        try {
            val opprettetDato = søknadForRiverClient.hentSoknadOpprettetDato(soknadData.soknadId)
            val tid = periodeMellomDatoer(
                LocalDateTime.ofInstant(opprettetDato.toInstant(), ZoneId.systemDefault()),
                LocalDateTime.now()
            )
            logger.info("Tid brukt fra opprettelse til godkjenning av søknad med ID ${soknadData.soknadId} var: $tid")
        } catch (e: Exception) {
            logger.info(e) { "Klarte ikke å måle tidbruk mellom opprettelse og godkjenning" }
        }
    }

    private fun forward(soknadData: SoknadData, context: MessageContext) {
        val fnrBruker = soknadData.fnrBruker
        val soknadId = soknadData.soknadId.toString()

        try {
            val soknadGodkjentMessage = soknadData.toJson("hm-søknadGodkjentAvBrukerMottatt")
            context.publish(fnrBruker, soknadGodkjentMessage)
            Prometheus.soknadGodkjentAvBrukerCounter.inc()
            logger.info("Søknad er godkjent av bruker: $soknadId - Det tok ")
            sikkerlogg.info("Søknad er godkjent med søknadsId: $soknadId, fnr: $fnrBruker)")
        } catch (e: Exception) {
            logger.error(e) { "Failed: ${e.message}. Soknad: $soknadId" }
        }
    }
}
