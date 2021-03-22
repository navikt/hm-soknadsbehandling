package no.nav.hjelpemidler.soknad.mottak.service

import com.fasterxml.jackson.databind.JsonNode
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageProblems
import java.time.LocalDateTime
import java.util.UUID

internal data class OrdrelinjeData(
    val soknadId: UUID,
    val fnrBruker: String,
    val serviceforespoersel: String?, // Viss det ikkje er ein SF
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
            it["eventId"] = UUID.randomUUID().toString()
            it["eventName"] = eventName
            it["opprettet"] = LocalDateTime.now()
            it["fnrBruker"] = this.fnrBruker
            it["artikkelnr"] = this.artikkelnr
            it["antall"] = this.antall
            it["produktgruppe"] = this.produktgruppe
        }.toJson()
    }
}
