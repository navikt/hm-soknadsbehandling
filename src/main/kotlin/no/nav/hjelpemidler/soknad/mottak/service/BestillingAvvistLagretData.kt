package no.nav.hjelpemidler.soknad.mottak.service

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.hjelpemidler.behovsmeldingsmodell.TilknyttetSøknad
import no.nav.hjelpemidler.soknad.mottak.river.Melding
import no.nav.hjelpemidler.soknad.mottak.river.jsonMessageOf
import java.time.LocalDateTime
import java.util.UUID

data class BestillingAvvistLagretData(
    override val søknadId: UUID,
    val fnrBruker: String,
    val opprettet: LocalDateTime,
    val begrunnelse: String,
    val valgteÅrsaker: List<String>,
) : TilknyttetSøknad, Melding {
    override fun toJsonMessage(eventName: String): JsonMessage = jsonMessageOf(
        "eventName" to eventName,
        "eventId" to UUID.randomUUID(),
        "søknadId" to søknadId,
        "fnrBruker" to fnrBruker,
        "opprettet" to opprettet,
        "begrunnelse" to begrunnelse,
        "valgteÅrsaker" to valgteÅrsaker,
    )
}
