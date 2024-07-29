package no.nav.hjelpemidler.soknad.mottak.client

import no.nav.hjelpemidler.soknad.mottak.service.Status
import java.time.Instant
import java.util.UUID

data class Søknad(
    val søknadId: UUID,
    val søknadOpprettet: Instant,
    val søknadEndret: Instant,
    val søknadGjelder: String,
    val fnrInnsender: String?,
    val fnrBruker: String,
    val navnBruker: String,
    val kommunenavn: String?,
    val journalpostId: String?,
    val oppgaveId: String?,
    val digital: Boolean,
    val behovsmeldingstype: BehovsmeldingType,
    val status: Status,
    val statusEndret: Instant,
)
