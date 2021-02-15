package no.nav.hjelpemidler.soknad.mottak.service

import com.fasterxml.jackson.databind.JsonNode
import com.github.guepardoapps.kulid.ULID
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageProblems
import java.time.LocalDateTime
import java.util.UUID

internal data class SoknadData(
    val fnrBruker: String,
    val fnrInnsender: String,
    val navnBruker: String,
    val soknadId: UUID,
    val soknadJson: String,
    val soknad: JsonNode,
    val status: Status
) {
    internal fun toJson(): String {
        return JsonMessage("{}", MessageProblems("")).also {
            it["@id"] = ULID.random()
            it["@event_name"] = "Søknad"
            it["@opprettet"] = LocalDateTime.now()
            it["fodselNrBruker"] = this.fnrBruker
            it["navnBruker"] = this.navnBruker
            it["soknad"] = this.soknad
            it["soknadId"] = this.soknadId
        }.toJson()
    }

    internal fun toVenterPaaGodkjenningJson(): String {
        return JsonMessage("{}", MessageProblems("")).also {
            it["@id"] = ULID.random()
            it["@event_name"] = "SøknadTilGodkjenning"
            it["@opprettet"] = LocalDateTime.now()
            it["fodselNrBruker"] = this.fnrBruker
            it["soknadId"] = this.soknadId
        }.toJson()
    }
}
