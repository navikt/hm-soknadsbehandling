package no.nav.hjelpemidler.soknad.mottak.service

import java.time.LocalDate
import java.util.UUID

internal data class VedtaksresultatData(
    val søknadId: UUID,
    val fnrBruker: String,
    val trygdekontorNr: String?,
    val saksblokk: String?,
    val saksnr: String?,
    val resultat: String?,
    val vedtaksdato: LocalDate?,
)
