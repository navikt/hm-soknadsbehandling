package no.nav.hjelpemidler.soknad.mottak.service

import java.util.UUID

class UtgåttSøknad(
    val søknadId: UUID? = null,
    val status: Status? = null,
    val fnrBruker: String? = null
)
