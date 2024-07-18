package no.nav.hjelpemidler.soknad.mottak.service

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageProblems
import java.time.LocalDateTime
import java.util.UUID

data class PapirSøknadData(
    val fnrBruker: String,
    val soknadId: UUID,
    val status: Status,
    val journalpostid: Int,
    val navnBruker: String,
) {
    fun toJson(eventName: String): String {
        return JsonMessage("{}", MessageProblems("")).also {
            it["eventId"] = UUID.randomUUID()
            it["eventName"] = eventName
            it["opprettet"] = LocalDateTime.now()
            it["fnrBruker"] = this.fnrBruker
            it["journalpostId"] = this.journalpostid
            it["soknadId"] = this.soknadId
        }.toJson()
    }
}
