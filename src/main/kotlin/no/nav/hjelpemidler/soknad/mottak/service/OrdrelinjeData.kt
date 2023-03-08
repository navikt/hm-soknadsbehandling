package no.nav.hjelpemidler.soknad.mottak.service

import com.fasterxml.jackson.databind.JsonNode
import mu.KotlinLogging
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageProblems
import java.util.UUID

private val logg = KotlinLogging.logger {}

internal data class OrdrelinjeData(
    val søknadId: UUID,
    val behovsmeldingType: BehovsmeldingType,
    val oebsId: Int,
    val fnrBruker: String,
    val serviceforespørsel: Int?, // Viss det ikkje er ein SF
    val ordrenr: Int,
    val ordrelinje: Int,
    val delordrelinje: Int,
    val artikkelnr: String,
    val antall: Double,
    val enhet: String,
    val produktgruppe: String,
    val produktgruppeNr: String,
    val hjelpemiddeltype: String,
    val data: JsonNode?,
) {
    internal fun toJson(eventName: String, søknadsType: String?): String {
        return JsonMessage("{}", MessageProblems("")).also {
            it["eventName"] = eventName
            it["eventId"] = UUID.randomUUID().toString()
            it["søknadId"] = this.søknadId
            it["fnrBruker"] = this.fnrBruker
            it["behovsmeldingType"] = this.behovsmeldingType
            søknadsType?.let { st ->
                logg.info("DEBUG: sender ordrelinje til ditt-nav med søknadsType=$st")
                it["søknadsType"] = st
            }
        }.toJson()
    }
}
