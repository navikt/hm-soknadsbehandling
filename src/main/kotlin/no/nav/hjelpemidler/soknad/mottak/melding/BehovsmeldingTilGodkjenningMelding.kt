package no.nav.hjelpemidler.soknad.mottak.melding

import com.fasterxml.jackson.annotation.JsonProperty
import no.nav.hjelpemidler.behovsmeldingsmodell.Behovsmeldingsgrunnlag
import no.nav.hjelpemidler.behovsmeldingsmodell.SøknadId
import no.nav.hjelpemidler.behovsmeldingsmodell.TilknyttetSøknad
import java.time.Instant
import java.util.UUID

data class BehovsmeldingTilGodkjenningMelding(
    @JsonProperty("soknadId")
    override val søknadId: SøknadId,
    val fnrBruker: String,
    val kommunenavn: String?,
) : TilknyttetSøknad, Melding {
    val opprettet: Instant = Instant.now()
    override val eventId: UUID = UUID.randomUUID()
    override val eventName: String = "hm-SøknadTilGodkjenning"

    @Deprecated("Bruk fnrBruker")
    @JsonProperty("fodselNrBruker")
    private val oldFnrBruker: String = fnrBruker

    @Deprecated("Bruk eventName")
    @JsonProperty("@event_name")
    private val oldEventName: String = "SøknadTilGodkjenning"

    constructor(grunnlag: Behovsmeldingsgrunnlag.Digital) : this(
        søknadId = grunnlag.søknadId,
        fnrBruker = grunnlag.fnrBruker,
        kommunenavn = "", // grunnlag.kommunenavn fixme
    )
}
