package no.nav.hjelpemidler.soknad.mottak.service

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.hjelpemidler.behovsmeldingsmodell.SøknadId
import no.nav.hjelpemidler.behovsmeldingsmodell.TilknyttetSøknad
import no.nav.hjelpemidler.soknad.mottak.river.Melding
import no.nav.hjelpemidler.soknad.mottak.river.jsonMessageOf
import java.time.LocalDateTime
import java.util.UUID

data class VedtaksresultatLagretData(
    override val søknadId: SøknadId,
    val fnrBruker: String,
    val vedtaksdato: LocalDateTime,
    val vedtaksresultat: String,
    val eksternVarslingDeaktivert: Boolean = false,
    val søknadstype: String? = null,
) : TilknyttetSøknad, Melding {
    override fun toJsonMessage(eventName: String): JsonMessage = jsonMessageOf(
        "eventName" to eventName,
        "eventId" to UUID.randomUUID(),
        "søknadId" to søknadId,
        "fnrBruker" to fnrBruker,
        "vedtaksdato" to vedtaksdato,
        "vedtaksresultat" to vedtaksresultat,
        "eksternVarslingDeaktivert" to eksternVarslingDeaktivert,
        "søknadsType" to søknadstype,
    )
}
