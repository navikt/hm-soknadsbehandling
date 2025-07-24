package no.nav.hjelpemidler.soknad.mottak.melding

import com.fasterxml.jackson.annotation.JsonProperty
import no.nav.hjelpemidler.behovsmeldingsmodell.BehovsmeldingId
import no.nav.hjelpemidler.behovsmeldingsmodell.BehovsmeldingType
import no.nav.hjelpemidler.behovsmeldingsmodell.Behovsmeldingsgrunnlag
import no.nav.hjelpemidler.behovsmeldingsmodell.SøknadDto
import no.nav.hjelpemidler.behovsmeldingsmodell.TilknyttetSøknad
import java.time.Instant
import java.util.UUID

data class BehovsmeldingMottattMelding(
    override val eventName: String,
    @JsonProperty("soknadId")
    override val søknadId: BehovsmeldingId,
    val fnrBruker: String,
    val fnrInnsender: String?,
    @JsonProperty("soknadGjelder")
    val behovsmeldingGjelder: String?,
    val behovsmeldingType: BehovsmeldingType
) : TilknyttetSøknad, Melding {
    val opprettet: Instant = Instant.now()
    override val eventId: UUID = UUID.randomUUID()

    @Deprecated("Bruk fnrBruker")
    @JsonProperty("fodselNrBruker")
    private val oldFnrBruker: String = fnrBruker

    @Deprecated("Bruk eventName")
    @JsonProperty("@event_name")
    private val oldEventName: String = eventName

    constructor(eventName: String, grunnlag: Behovsmeldingsgrunnlag.Digital, behovsmeldingType: BehovsmeldingType) : this(
        eventName = eventName,
        søknadId = grunnlag.søknadId,
        fnrBruker = grunnlag.fnrBruker,
        fnrInnsender = grunnlag.fnrInnsender,
        behovsmeldingGjelder = grunnlag.behovsmeldingGjelder,
        behovsmeldingType = behovsmeldingType,
    )

    constructor(eventName: String, søknad: SøknadDto) : this(
        eventName = eventName,
        søknadId = søknad.søknadId,
        fnrBruker = søknad.fnrBruker,
        fnrInnsender = søknad.fnrInnsender,
        behovsmeldingGjelder = søknad.søknadGjelder,
        behovsmeldingType = søknad.behovsmeldingstype,
    )
}
