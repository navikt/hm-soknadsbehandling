package no.nav.hjelpemidler.soknad.mottak.service

import no.nav.hjelpemidler.behovsmeldingsmodell.BehovsmeldingStatus

@Deprecated(
    "Bruk BehovsmeldingStatus direkte",
    ReplaceWith("no.nav.hjelpemidler.behovsmeldingsmodell.BehovsmeldingStatus")
)
typealias Status = BehovsmeldingStatus
