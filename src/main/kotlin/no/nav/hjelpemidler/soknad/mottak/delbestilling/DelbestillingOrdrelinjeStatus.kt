package no.nav.hjelpemidler.soknad.mottak.delbestilling

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.module.kotlin.convertValue
import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.hjelpemidler.soknad.mottak.jsonMapper
import no.nav.hjelpemidler.soknad.mottak.river.AsyncPacketListener
import java.time.LocalDate

private val logger = KotlinLogging.logger {}

class DelbestillingOrdrelinjeStatus(
    rapidsConnection: RapidsConnection,
    private val delbestillingClient: DelbestillingClient,
) : AsyncPacketListener {
    init {
        River(rapidsConnection).apply {
            validate { it.demandAny("eventName", listOf("hm-uvalidert-ordrelinje")) }
            validate { it.requireKey("eventId", "eventCreated", "orderLine") }
        }.register(this)
    }

    private val JsonMessage.ordrelinje get() = jsonMapper.convertValue<Ordrelinje>(this["orderLine"])

    override suspend fun onPacketAsync(packet: JsonMessage, context: MessageContext) {
        try {
            val ordrelinje = packet.ordrelinje
            val ordrenummer = ordrelinje.ordrenr
            val hmsnr = ordrelinje.artikkelnr
            val hjelpemiddeltype = ordrelinje.hjelpemiddeltype
            val datoOppdatert = ordrelinje.sistOppdatert

            if (hjelpemiddeltype == "Del") {
                /*
                    Her vet vi egentlig ikke om dette er skipningsbekreftelse for en delbestilling,
                    men hm-delbestilling-api vet det utifra om den kjenner igjen ordrenummeret.
                    Så vi sender alt til hm-delbestilling-api, så må den ignorere det som ikke er relatert til delbestillinger.
                 */
                logger.info { "Mottok skipningsbekreftelse for ordrenummer: $ordrenummer på hmsnr: $hmsnr" }
                delbestillingClient.oppdaterDellinjeStatus(
                    ordrenummer,
                    Status.SKIPNINGSBEKREFTET,
                    hmsnr,
                    datoOppdatert
                )
            } else {
                logger.info { "Ignorerer skipningsbekreftelse for ordrenummer: $ordrenummer med hjelpemiddeltype: $hjelpemiddeltype" }
            }

        } catch (e: Exception) {
            logger.error(e) { "Håndtering av hm-uvalidert-ordrelinje event feilet" }
            throw e
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
