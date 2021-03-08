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
    val soknadId: UUID,
    val soknad: JsonNode,
    val status: Status,
    val kommunenavn: String?
) {
    internal fun toJson(eventName: String): String {
        return JsonMessage("{}", MessageProblems("")).also {
            it["@id"] = ULID.random()
            it["@event_name"] = eventName
            it["@opprettet"] = LocalDateTime.now()
            it["fodselNrBruker"] = this.fnrBruker
            it["navnBruker"] = this.soknad["soknad"]["bruker"]["etternavn"].textValue() + " " + this.soknad["soknad"]["bruker"]["fornavn"].textValue()
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
            it["kommunenavn"] = this.kommunenavn ?: ""
        }.toJson()
    }
}
