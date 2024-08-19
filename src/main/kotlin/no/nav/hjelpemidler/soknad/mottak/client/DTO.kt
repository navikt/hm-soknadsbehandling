package no.nav.hjelpemidler.soknad.mottak.client

import com.fasterxml.jackson.annotation.JsonAlias
import no.nav.hjelpemidler.behovsmeldingsmodell.BehovsmeldingStatus
import java.time.LocalDate
import java.util.UUID

class UtgåttSøknad(
    val søknadId: UUID? = null,
    val status: BehovsmeldingStatus? = null,
    val fnrBruker: String? = null,
)

data class SøknadIdFraVedtaksresultat(
    val søknadId: UUID,
    @JsonAlias("vedtaksDato")
    val vedtaksdato: LocalDate?,
)

data class HarOrdre(
    val harOrdreAvTypeHjelpemidler: Boolean,
    val harOrdreAvTypeDel: Boolean,
)
