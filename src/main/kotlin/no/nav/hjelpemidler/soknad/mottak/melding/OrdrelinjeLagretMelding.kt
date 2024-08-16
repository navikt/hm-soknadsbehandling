package no.nav.hjelpemidler.soknad.mottak.melding

import no.nav.hjelpemidler.behovsmeldingsmodell.BehovsmeldingType
import no.nav.hjelpemidler.behovsmeldingsmodell.SøknadId
import no.nav.hjelpemidler.behovsmeldingsmodell.TilknyttetSøknad
import no.nav.hjelpemidler.behovsmeldingsmodell.ordre.Ordrelinje
import java.util.UUID

data class OrdrelinjeLagretMelding(
    override val søknadId: SøknadId,
    val fnrBruker: String,
    val behovsmeldingType: BehovsmeldingType,
) : TilknyttetSøknad, Melding {
    override val eventId: UUID = UUID.randomUUID()
    override val eventName: String = "hm-OrdrelinjeLagret"

    constructor(ordrelinje: Ordrelinje, behovsmeldingType: BehovsmeldingType) : this(
        søknadId = ordrelinje.søknadId,
        fnrBruker = ordrelinje.fnrBruker,
        behovsmeldingType = behovsmeldingType
    )
}
