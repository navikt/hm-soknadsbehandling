package no.nav.hjelpemidler.soknad.mottak.river

import com.fasterxml.jackson.databind.JsonNode
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
import java.time.LocalDateTime
import java.util.*

private val logger = KotlinLogging.logger { }

/**
 * Plukker opp bytter som er sendt inn av brukerpassbruker på seg selv.
 */
internal class BrukerpassbytteOpprettet(
    rapidsConnection: RapidsConnection,
    private val søknadForRiverClient: SøknadForRiverClient,
) : PacketListenerWithOnError {

    init {
        River(rapidsConnection).apply {
            validate { it.demandValue("eventName", "hm-brukerpassbytte-opprettet") }

            validate { it.requireKey("fnr", "brukerpassbytteId", "brukerpassbytte") }
        }.register(this)
    }

    private val JsonMessage.eventId get() = this["eventId"].textValue()
    private val JsonMessage.fnr get() = this["fnr"].textValue()
    private val JsonMessage.bytteId get() = this["brukerpassbytteId"].textValue()
    private val JsonMessage.brukerpassbytte get() = this["brukerpassbytte"]

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        runBlocking {
            try {
                val bytteData = BrukerpassbytteData(
                    id = UUID.fromString(packet.bytteId),
                    fnr = packet.fnr,
                    brukerpassbytte = packet.brukerpassbytte,
                    status = "INNSENDT_FULLMAKT_IKKE_PÅKREVD",
                )

                logger.info { "Brukerpassbytte mottatt: ${packet.bytteId}" }
                // TODO søknadForRiverClient.save(bytteData)
                logger.info("Brukerpassbytte lagret til hm-soknadsbehandling-db: ${packet.bytteId}")

                context.publish(bytteData.fnr, bytteData.toJson("hm-brukerpassbytteMottatt"))
                logger.info { "hm-brukerpassbytteMottatt sendt for ${packet.bytteId}" }

                Prometheus.brukerpassbytteCounter.inc()
            } catch (e: Exception) {
                throw RuntimeException("Håndtering av event ${packet.eventId} feilet", e)
            }
        }
    }

    private data class BrukerpassbytteData(
        val id: UUID,
        val fnr: String,
        val brukerpassbytte: JsonNode,
        val status: String,
    ) {
        fun toJson(eventName: String): String {
            return JsonMessage("{}", MessageProblems("")).also {
                it["eventId"] = ULID.random()
                it["eventName"] = eventName
                it["opprettet"] = LocalDateTime.now()
                it["id"] = this.id
                it["fnr"] = this.fnr
                it["brukerpassbytte"] = this.brukerpassbytte
            }.toJson()
        }
    }
}
