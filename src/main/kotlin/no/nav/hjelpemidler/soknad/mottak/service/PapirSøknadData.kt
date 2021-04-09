package no.nav.hjelpemidler.soknad.mottak.service

import com.github.guepardoapps.kulid.ULID
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageProblems
import java.time.LocalDateTime
import java.util.UUID

internal data class PapirSøknadData(
    val fnrBruker: String,
    val soknadId: UUID,
    val status: Status,
    val journalpostid: Int,
    val navnBruker: String,
) {
    internal fun toJson(eventName: String): String {
        return JsonMessage("{}", MessageProblems("")).also {
            val id = ULID.random()
            it["eventId"] = id
            it["eventName"] = eventName
            it["opprettet"] = LocalDateTime.now()
            it["fnrBruker"] = this.fnrBruker
            it["journalpostId"] = this.journalpostid
            it["soknadId"] = this.soknadId
        }.toJson()
    }
}
