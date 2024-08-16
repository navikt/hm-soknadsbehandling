package no.nav.hjelpemidler.soknad.mottak.melding

import com.fasterxml.jackson.annotation.JsonProperty
import no.nav.hjelpemidler.behovsmeldingsmodell.SøknadId
import no.nav.hjelpemidler.behovsmeldingsmodell.TilknyttetSøknad
import no.nav.hjelpemidler.behovsmeldingsmodell.sak.InfotrygdSakId
import java.util.UUID

data class OvervåkVedtaksresultatMelding(
    override val søknadId: SøknadId,
    val fnrBruker: String,
    @JsonProperty("trygdekontorNr")
    val trygdekontornummer: String,
    val saksblokk: String,
    @JsonProperty("saksnr")
    val saksnummer: String,
) : TilknyttetSøknad, Melding {
    override val eventId: UUID = UUID.randomUUID()
    override val eventName: String = "hm-InfotrygdAddToPollVedtakList"

    constructor(søknadId: SøknadId, fnrBruker: String, fagsakId: InfotrygdSakId) : this(
        søknadId = søknadId,
        fnrBruker = fnrBruker,
        trygdekontornummer = fagsakId.trygdekontornummer,
        saksblokk = fagsakId.saksblokk,
        saksnummer = fagsakId.saksnummer,
    )
}
