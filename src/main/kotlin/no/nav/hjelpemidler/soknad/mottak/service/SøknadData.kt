package no.nav.hjelpemidler.soknad.mottak.service

import com.fasterxml.jackson.databind.JsonNode
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageProblems
import java.time.LocalDateTime
import java.util.UUID

data class SøknadData(
    val fnrBruker: String,
    val navnBruker: String, // Lagres til hm-soknadsbehandling-db, slik at det kan vises i hm-formidler, selv om bruker sletter søknaden (brukerbekreftelse)
    val fnrInnsender: String,
    val soknadId: UUID,
    val soknad: JsonNode,
    val status: Status,
    val kommunenavn: String?,
    val soknadGjelder: String?,
) {

    companion object {
        fun mapFraDto(soknadDataDto: SoknadDataDto): SøknadData {
            requireNotNull(soknadDataDto.fnrBruker)
            requireNotNull(soknadDataDto.navnBruker)
            requireNotNull(soknadDataDto.fnrInnsender)
            requireNotNull(soknadDataDto.soknadId)
            requireNotNull(soknadDataDto.soknad)
            requireNotNull(soknadDataDto.status)
            return SøknadData(
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

    fun toJson(eventName: String): String {
        return JsonMessage("{}", MessageProblems("")).also {
            val id = UUID.randomUUID()
            it["@id"] = id // @deprecated
            it["eventId"] = id
            it["@event_name"] = eventName // @deprecated
            it["eventName"] = eventName
            it["opprettet"] = LocalDateTime.now()
            it["fodselNrBruker"] = this.fnrBruker // @deprecated
            it["fnrBruker"] = this.fnrBruker
            it["soknad"] = this.soknad
            it["soknadId"] = this.soknadId
            it["fnrInnsender"] = this.fnrInnsender
            if (this.soknadGjelder != null) it["soknadGjelder"] = this.soknadGjelder
        }.toJson()
    }

    fun toVenterPaaGodkjenningJson(): String {
        return JsonMessage("{}", MessageProblems("")).also {
            val id = UUID.randomUUID()
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
