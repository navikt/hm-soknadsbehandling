package no.nav.hjelpemidler.soknad.mottak.service

import com.fasterxml.jackson.databind.JsonNode
import com.github.guepardoapps.kulid.ULID
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageProblems
import java.time.LocalDateTime
import java.util.UUID

data class BrukerpassbytteData(
    val id: UUID,
    val fnr: String,
    val brukerpassbytte: JsonNode,
    val status: String,
    val soknadGjelder: String,
) {
    fun toJson(eventName: String): String {
        return JsonMessage("{}", MessageProblems("")).also {
            it["eventId"] = ULID.random()
            it["eventName"] = eventName
            it["opprettet"] = LocalDateTime.now()
            it["id"] = this.id
            it["fnr"] = this.fnr
            it["brukerpassbytte"] = this.brukerpassbytte
            it["soknadGjelder"] = this.soknadGjelder
        }.toJson()
    }
}