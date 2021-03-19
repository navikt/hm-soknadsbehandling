package no.nav.hjelpemidler.soknad.mottak.service

import com.fasterxml.jackson.databind.JsonNode
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageProblems
import java.time.LocalDateTime
import java.util.*

internal data class OrdrelinjeData(
        val soknadId: UUID,
        val fnrBruker: String,
        val serviceforespoersel: String?, // Viss det ikkje er ein SF
        val ordrenr: Int,
        val ordrelinje: String,
        val vedtaksdato: String?, // Viss det ikkje er ein SF
        val artikkelnummer: String,
        val antall: Int,
        val data: JsonNode,
) {
    internal fun toJson(eventName: String, eventId: UUID, ): String {
        return JsonMessage("{}", MessageProblems("")).also {
            it["eventId"] = eventId.toString()
            it["eventName"] = eventName
            it["opprettet"] = LocalDateTime.now()
            it["fnrBruker"] = this.fnrBruker
        }.toJson()
    }

}
