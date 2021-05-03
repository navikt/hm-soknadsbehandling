package no.nav.hjelpemidler.soknad.mottak.service

import com.fasterxml.jackson.databind.JsonNode
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageProblems
import java.util.UUID

internal data class OrdrelinjeData(
    val søknadId: UUID,
    val fnrBruker: String,
    val serviceforespørsel: Int?, // Viss det ikkje er ein SF
    val ordrenr: Int,
    val ordrelinje: Int,
    val delordrelinje: Int,
    val artikkelnr: String,
    val antall: Int,
    val produktgruppe: String,
    val data: JsonNode,
) {
    internal fun toJson(eventName: String): String {
        return JsonMessage("{}", MessageProblems("")).also {
            it["eventName"] = eventName
            it["eventId"] = UUID.randomUUID().toString()
            it["søknadId"] = this.søknadId
            it["fnrBruker"] = this.fnrBruker
        }.toJson()
    }
}
