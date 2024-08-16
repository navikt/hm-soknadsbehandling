package no.nav.hjelpemidler.soknad.mottak.melding

import com.fasterxml.jackson.annotation.JsonProperty
import no.nav.hjelpemidler.behovsmeldingsmodell.TilknyttetSøknad
import java.time.LocalDateTime
import java.util.UUID

data class VedtaksresultatLagretMelding(
    override val søknadId: UUID,
    val fnrBruker: String,
    val vedtaksdato: LocalDateTime,
    val vedtaksresultat: String,
    val eksternVarslingDeaktivert: Boolean,
    @JsonProperty("søknadsType")
    val søknadstype: String?,
    override val eventName: String,
) : TilknyttetSøknad, Melding {
    override val eventId: UUID = UUID.randomUUID()
}
