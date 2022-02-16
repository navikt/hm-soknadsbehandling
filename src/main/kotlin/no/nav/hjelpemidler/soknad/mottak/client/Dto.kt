package no.nav.hjelpemidler.soknad.mottak.service

import com.fasterxml.jackson.databind.JsonNode
import java.time.LocalDate
import java.util.Date
import java.util.UUID

data class SoknadDataDto(
    val fnrBruker: String? = null,
    val navnBruker: String? = null,
    val fnrInnsender: String? = null,
    val soknadId: UUID? = null,
    val soknad: JsonNode? = null,
    val status: Status? = null,
    val kommunenavn: String? = null,
    val soknadGjelder: String? = null,
)

data class SoknadMedStatus(
    val soknadId: UUID? = null,
    val datoOpprettet: Date? = null,
    val datoOppdatert: Date? = null,
    val status: Status? = null,
    val fullmakt: Boolean? = null,
    val formidlerNavn: String? = null
)

class UtgåttSøknad(
    val søknadId: UUID? = null,
    val status: Status? = null,
    val fnrBruker: String? = null
)

data class SøknadIdFraVedtaksresultat(
    val søknadId: UUID,
    val vedtaksDato: LocalDate?,
)

data class HarOrdre (
    val harOrdreAvTypeHjelpemidler: Boolean,
    val harOrdreAvTypeDel: Boolean,
)