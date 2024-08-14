package no.nav.hjelpemidler.soknad.mottak.service

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.hjelpemidler.behovsmeldingsmodell.BehovsmeldingType
import no.nav.hjelpemidler.behovsmeldingsmodell.TilknyttetSøknad
import no.nav.hjelpemidler.soknad.mottak.river.Melding
import no.nav.hjelpemidler.soknad.mottak.river.jsonMessageOf
import java.util.UUID

data class SøknadUnderBehandlingData(
    override val søknadId: UUID,
    val fnrBruker: String,
    val behovsmeldingType: BehovsmeldingType,
) : TilknyttetSøknad, Melding {
    override fun toJsonMessage(eventName: String): JsonMessage = jsonMessageOf(
        "eventName" to eventName,
        "eventId" to UUID.randomUUID(),
        "søknadId" to this.søknadId,
        "fnrBruker" to this.fnrBruker,
        "behovsmeldingType" to this.behovsmeldingType,
    )
}
