package no.nav.hjelpemidler.soknad.mottak.river

import com.github.guepardoapps.kulid.ULID
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.MessageProblems
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.hjelpemidler.soknad.mottak.client.SøknadForRiverClient
import no.nav.hjelpemidler.soknad.mottak.metrics.Prometheus
import no.nav.hjelpemidler.soknad.mottak.service.Status
import java.time.LocalDateTime
import java.util.UUID

private val logger = KotlinLogging.logger {}
private val sikkerlogg = KotlinLogging.logger("tjenestekall")

internal class SlettSoknad(rapidsConnection: RapidsConnection, private val søknadForRiverClient: SøknadForRiverClient) :
    PacketListenerWithOnError {

    init {
        River(rapidsConnection).apply {
            validate { it.demandValue("eventName", "slettetAvBruker") }
            validate { it.requireKey("soknadId") }
        }.register(this)
    }

    private val JsonMessage.soknadId get() = this["soknadId"].textValue()

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        runBlocking {
            try {
                logger.info { "Bruker har slettet søknad: ${packet.soknadId}" }
                val rowsUpdated = update(UUID.fromString(packet.soknadId), Status.SLETTET)
                if (rowsUpdated > 0) {
                    val fnrBruker = søknadForRiverClient.hentFnrForSoknad(UUID.fromString(packet.soknadId))
                    forward(UUID.fromString(packet.soknadId), fnrBruker, context)
                } else {
                    logger.info { "Søknad som slettes er allerede slettet eller stod ikke til godkjenning, søknadId: ${packet.soknadId}" }
                }
            } catch (e: Exception) {
                throw RuntimeException("Håndtering av brukers sletting av søknad ${packet.soknadId} feilet", e)
            }
        }
    }

    private suspend fun update(soknadId: UUID, status: Status) =
        kotlin.runCatching {
            søknadForRiverClient.slettSøknad(soknadId)
        }.onSuccess {
            logger.info("Søknad $soknadId oppdatert med status $status")
        }.onFailure {
            logger.error(it) { "Failed to update søknad $soknadId med status $status" }
        }.getOrThrow()

    private fun forward(soknadId: UUID, fnrBruker: String, context: MessageContext) {
        try {

            val soknadGodkjentMessage = JsonMessage("{}", MessageProblems("")).also {
                it["@id"] = ULID.random()
                it["@event_name"] = "SøknadSlettetAvBruker"
                it["@opprettet"] = LocalDateTime.now()
                it["fodselNrBruker"] = fnrBruker
                it["soknadId"] = soknadId.toString()
            }.toJson()
            context.publish(fnrBruker, soknadGodkjentMessage)
            Prometheus.soknadSlettetAvBrukerCounter.inc()
            logger.info("Søknad er slettet av bruker: $soknadId")
            sikkerlogg.info("Søknad er slettet med søknadsId: $soknadId, fnr: $fnrBruker)")
        } catch (e: Exception) {
            logger.error(e) { "Failed: ${e.message}. Soknad: $soknadId" }
        }
    }
}
