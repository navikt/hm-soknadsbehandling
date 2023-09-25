package no.nav.hjelpemidler.soknad.mottak.delbestilling

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.module.kotlin.convertValue
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helse.rapids_rivers.asLocalDateTime
import no.nav.hjelpemidler.soknad.mottak.river.PacketListenerWithOnError
import java.time.LocalDate
import java.util.UUID

private val logger = KotlinLogging.logger {}

internal class DelbestillingOrdrelinjeStatus(
    rapidsConnection: RapidsConnection,
    private val delbestillingClient: DelbestillingClient,
) : PacketListenerWithOnError {

    init {
        River(rapidsConnection).apply {
            validate { it.demandAny("eventName", listOf("hm-uvalidert-ordrelinje")) }
            validate { it.requireKey("eventId", "eventCreated", "orderLine") }
        }.register(this)
    }

    private val JsonMessage.eventId get() = this["eventId"].textValue()
    private val JsonMessage.opprettet get() = this["eventCreated"].asLocalDateTime()
    private val JsonMessage.ordrelinje get() = jsonMapper.convertValue<Ordrelinje>(this["orderLine"])

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        runBlocking {
            try {
                val eventId = UUID.fromString(packet.eventId)
                val opprettet = packet.opprettet
                val ordrelinje = packet.ordrelinje

                val ordrenummer = ordrelinje.ordrenr
                val hmsnr = ordrelinje.artikkelnr

                logger.info { "Mottok skipningsbekreftelse for ordrenummer $ordrenummer på hmsnr $hmsnr" }
            } catch (e: Exception) {
                logger.error(e) { "Parsing av hm-uvalidert-ordrelinje event feilet" }
            }
        }
    }
}

private data class Ordrelinje(
    val mottakendeSystem: String,
    val oebsId: Int,
    val serviceforespørsel: Int,
    val serviceforespørselstatus: String,
    val serviceforespørseltype: String,
    val søknadstype: String,
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    val vedtaksdato: LocalDate?,
    val søknad: String,
    val hotSakSaksnummer: String?,
    val kilde: String?,
    val resultat: String,
    val saksblokkOgSaksnr: String?,
    val ordrenr: Int,
    val ordrelinje: Int,
    val delordrelinje: Int,
    val artikkelbeskrivelse: String,
    val produktgruppe: String,
    val produktgruppeNr: String,
    val artikkelnr: String,
    val hjelpemiddeltype: String,
    val antall: Double,
    val enhet: String,
    val fnrBruker: String,
    val egenAnsatt: String,
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    val sistOppdatert: LocalDate,
    val sendtTilAdresse: String,
    var serienumre: List<String> = listOf(),
)
