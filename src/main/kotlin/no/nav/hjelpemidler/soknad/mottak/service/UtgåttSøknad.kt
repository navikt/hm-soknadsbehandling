package no.nav.hjelpemidler.soknad.mottak.service

import java.util.UUID

class UtgåttSøknad(
    val søknadId: UUID,
    val status: Status,
    val fnrBruker: String
)
