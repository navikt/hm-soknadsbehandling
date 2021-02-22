package no.nav.hjelpemidler.soknad.mottak.service

import com.fasterxml.jackson.databind.JsonNode
import java.util.Date
import java.util.UUID

data class SoknadForBruker(
    val fnrBruker: String,
    val soknadId: UUID,
    val datoOpprettet: Date,
    val soknad: JsonNode,
    val status: Status
) {
    /*internal fun toJson(): String {
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
    }*/
}
