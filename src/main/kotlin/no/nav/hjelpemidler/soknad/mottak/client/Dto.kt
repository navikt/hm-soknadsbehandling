package no.nav.hjelpemidler.soknad.mottak.service

import com.fasterxml.jackson.databind.JsonNode
import java.util.Date
import java.util.UUID

data class SoknadDataDto(
    val fnrBruker: String? = null,
    val navnBruker: String? = null,
    val fnrInnsender: String? = null,
    val soknadId: UUID? = null,
    val soknad: JsonNode? = null,
    val status: Status? = null,
    val kommunenavn: String? = null
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
