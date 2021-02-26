package no.nav.hjelpemidler.soknad.mottak.service

import java.util.Date
import java.util.UUID

class SoknadMedStatus(
    val soknadId: UUID,
    val datoOpprettet: Date,
    val status: Status
)
