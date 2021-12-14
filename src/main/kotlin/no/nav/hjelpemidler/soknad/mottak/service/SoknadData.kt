package no.nav.hjelpemidler.soknad.mottak.service

import com.fasterxml.jackson.databind.JsonNode
import com.github.guepardoapps.kulid.ULID
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageProblems
import java.time.LocalDateTime
import java.util.UUID

internal data class SoknadData(
    val fnrBruker: String,
    val navnBruker: String,
    val fnrInnsender: String,
    val soknadId: UUID,
    val soknad: JsonNode,
    val status: Status,
    val kommunenavn: String?,
    val soknadGjelder: String?,
) {

    companion object {
        fun mapFraDto(soknadDataDto: SoknadDataDto): SoknadData {
            requireNotNull(soknadDataDto.fnrBruker)
            requireNotNull(soknadDataDto.navnBruker)
            requireNotNull(soknadDataDto.fnrInnsender)
            requireNotNull(soknadDataDto.soknadId)
            requireNotNull(soknadDataDto.soknad)
            requireNotNull(soknadDataDto.status)
            return SoknadData(
                soknadDataDto.fnrBruker,
                soknadDataDto.navnBruker,
                soknadDataDto.fnrInnsender,
                soknadDataDto.soknadId,
                soknadDataDto.soknad,
                soknadDataDto.status,
                soknadDataDto.kommunenavn,
                soknadDataDto.soknadGjelder,
            )
        }
    }
    internal fun toJson(eventName: String): String {
        return JsonMessage("{}", MessageProblems("")).also {
            val id = ULID.random()
            it["@id"] = id // @deprecated
            it["eventId"] = id
            it["@event_name"] = eventName // @deprecated
            it["eventName"] = eventName
            it["opprettet"] = LocalDateTime.now()
            it["fodselNrBruker"] = this.fnrBruker // @deprecated
            it["fnrBruker"] = this.fnrBruker
            it["navnBruker"] = this.soknad["soknad"]["bruker"]["etternavn"].textValue() + " " + this.soknad["soknad"]["bruker"]["fornavn"].textValue()
            it["soknad"] = this.soknad
            it["soknadId"] = this.soknadId
            it["fnrInnsender"] = this.fnrInnsender
            if (this.soknadGjelder != null) it["soknadGjelder"] = this.soknadGjelder
        }.toJson()
    }

    internal fun toVenterPaaGodkjenningJson(): String {
        return JsonMessage("{}", MessageProblems("")).also {
            val id = ULID.random()
            it["@id"] = id // @deprecated
            it["eventId"] = id
            it["@event_name"] = "SøknadTilGodkjenning" // @deprecated
            it["eventName"] = "hm-SøknadTilGodkjenning"
            it["opprettet"] = LocalDateTime.now()
            it["fodselNrBruker"] = this.fnrBruker // @deprecated
            it["fnrBruker"] = this.fnrBruker
            it["soknadId"] = this.soknadId
            it["kommunenavn"] = this.kommunenavn ?: ""
        }.toJson()
    }
}
