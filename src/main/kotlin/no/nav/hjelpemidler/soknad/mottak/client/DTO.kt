package no.nav.hjelpemidler.soknad.mottak.service

import com.fasterxml.jackson.databind.JsonNode
import no.nav.hjelpemidler.behovsmeldingsmodell.BehovsmeldingStatus
import java.time.LocalDate
import java.util.Date
import java.util.UUID

data class SoknadDataDto(
    val fnrBruker: String? = null,
    val navnBruker: String? = null,
    val fnrInnsender: String? = null,
    val soknadId: UUID? = null,
    val soknad: JsonNode? = null,
    val status: BehovsmeldingStatus? = null,
    val kommunenavn: String? = null,
    val soknadGjelder: String? = null,
)

data class SoknadMedStatus(
    val soknadId: UUID? = null,
    val datoOpprettet: Date? = null,
    val datoOppdatert: Date? = null,
    val status: BehovsmeldingStatus? = null,
    val fullmakt: Boolean? = null,
    val formidlerNavn: String? = null,
)

class UtgåttSøknad(
    val søknadId: UUID? = null,
    val status: BehovsmeldingStatus? = null,
    val fnrBruker: String? = null,
)

data class SøknadIdFraVedtaksresultat(
    val søknadId: UUID,
    val vedtaksDato: LocalDate?,
)

data class HarOrdre(
    val harOrdreAvTypeHjelpemidler: Boolean,
    val harOrdreAvTypeDel: Boolean,
)

@Deprecated("Bruk BehovsmeldingType direkte", ReplaceWith("no.nav.hjelpemidler.behovsmeldingsmodell.BehovsmeldingType"))
typealias BehovsmeldingType = no.nav.hjelpemidler.behovsmeldingsmodell.BehovsmeldingType
