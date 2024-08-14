package no.nav.hjelpemidler.soknad.mottak.delbestilling

import com.fasterxml.jackson.module.kotlin.convertValue
import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helse.rapids_rivers.asLocalDateTime
import no.nav.hjelpemidler.soknad.mottak.jsonMapper
import no.nav.hjelpemidler.soknad.mottak.river.AsyncPacketListener
import no.nav.hjelpemidler.soknad.mottak.river.uuidValue

private val logger = KotlinLogging.logger {}

class DelbestillingStatus(
    rapidsConnection: RapidsConnection,
    private val delbestillingClient: DelbestillingClient,
) : AsyncPacketListener {
    init {
        River(rapidsConnection).apply {
            validate { it.demandAny("eventName", listOf("hm-ordrekvittering-delbestilling-mottatt")) }
            validate { it.requireKey("eventId", "opprettet", "kvittering") }
        }.register(this)
    }

    private val JsonMessage.eventId get() = uuidValue("eventId")
    private val JsonMessage.opprettet get() = this["opprettet"].asLocalDateTime()
    private val JsonMessage.kvittering get() = jsonMapper.convertValue<Ordrekvittering>(this["kvittering"])

    override suspend fun onPacketAsync(packet: JsonMessage, context: MessageContext) {
        val kvittering = packet.kvittering
        val saksnummer = kvittering.saksnummer
        val status = kvittering.status
        val ordrenummer = kvittering.ordrenummer

        logger.info { "Oppdaterer status for delbestilling med saksnummer: $saksnummer (hmdel_$saksnummer, ordrenummer: $ordrenummer) til status: $status" }
        delbestillingClient.oppdaterStatus(saksnummer, status, ordrenummer)
        logger.info { "Status for delbestilling med saksnummer: $saksnummer (hmdel_$saksnummer, ordrenummer: $ordrenummer) oppdatert OK" }
    }
}

private data class Ordrekvittering(
    val id: String,
    val saksnummer: String,
    val ordrenummer: String,
    val system: String,
    val status: Status,
)
