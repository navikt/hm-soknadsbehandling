package no.nav.hjelpemidler.soknad.mottak.soknadsbehandling

import no.nav.hjelpemidler.behovsmeldingsmodell.BehovsmeldingStatus

data class Statusendring(
    val status: BehovsmeldingStatus,
    val valgteÅrsaker: Set<String>? = null,
    val begrunnelse: String? = null,
)
