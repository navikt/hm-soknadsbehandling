package no.nav.hjelpemidler.soknad.mottak.delbestilling

import com.fasterxml.jackson.module.kotlin.convertValue
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helse.rapids_rivers.asLocalDateTime
import no.nav.hjelpemidler.soknad.mottak.river.PacketListenerWithOnError
import java.util.UUID

data class Ordrekvittering(
    val id: String,
    val saksnummer: String,
    val ordrenummer: String,
    val system: String,
    val status: Status,
)

internal class DelbestillingStatus(
    rapidsConnection: RapidsConnection,
    private val delbestillingClient: DelbestillingClient,
) : PacketListenerWithOnError {
    companion object {
        private val logger = KotlinLogging.logger {}
    }

    init {
        River(rapidsConnection).apply {
            validate { it.demandAny("eventName", listOf("hm-ordrekvittering-delbestilling-mottatt")) }
            validate { it.requireKey("eventId", "opprettet", "kvittering") }
        }.register(this)
    }

    private val JsonMessage.eventId get() = this["eventId"].textValue()
    private val JsonMessage.opprettet get() = this["opprettet"].asLocalDateTime()
    private val JsonMessage.kvittering get() = jsonMapper.convertValue<Ordrekvittering>(this["kvittering"])

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        runBlocking {
            val eventId = UUID.fromString(packet.eventId)
            val opprettet = packet.opprettet
            val kvittering = packet.kvittering

            delbestillingClient.oppdaterStatus(kvittering.saksnummer, kvittering.status)
        }
    }
}
