package no.nav.hjelpemidler.soknad.mottak.melding

import no.nav.hjelpemidler.behovsmeldingsmodell.BehovsmeldingType
import no.nav.hjelpemidler.behovsmeldingsmodell.BehovsmeldingId
import no.nav.hjelpemidler.behovsmeldingsmodell.TilknyttetSøknad
import java.util.UUID

data class SøknadUnderBehandlingMelding(
    override val søknadId: BehovsmeldingId,
    val fnrBruker: String,
    val behovsmeldingType: BehovsmeldingType,
) : TilknyttetSøknad, Melding {
    override val eventId: UUID = UUID.randomUUID()
    override val eventName: String = "hm-SøknadUnderBehandling"
}
