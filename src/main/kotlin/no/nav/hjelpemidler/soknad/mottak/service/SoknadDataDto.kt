package no.nav.hjelpemidler.soknad.mottak.service

import com.fasterxml.jackson.databind.JsonNode
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
