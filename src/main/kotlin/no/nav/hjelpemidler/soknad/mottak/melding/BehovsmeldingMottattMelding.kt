package no.nav.hjelpemidler.soknad.mottak.melding

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.convertValue
import no.nav.hjelpemidler.behovsmeldingsmodell.BehovsmeldingId
import no.nav.hjelpemidler.behovsmeldingsmodell.Behovsmeldingsgrunnlag
import no.nav.hjelpemidler.behovsmeldingsmodell.SøknadDto
import no.nav.hjelpemidler.behovsmeldingsmodell.TilknyttetSøknad
import no.nav.hjelpemidler.serialization.jackson.jsonMapper
import java.time.Instant
import java.util.UUID

data class BehovsmeldingMottattMelding(
    override val eventName: String,
    @JsonProperty("soknadId")
    override val søknadId: BehovsmeldingId,
    val fnrBruker: String,
    val fnrInnsender: String?,
    @JsonProperty("soknad")
    val behovsmelding: Map<String, Any?>,
    @JsonProperty("soknadGjelder")
    val behovsmeldingGjelder: String?,
) : TilknyttetSøknad, Melding {
    val opprettet: Instant = Instant.now()
    override val eventId: UUID = UUID.randomUUID()

    @Deprecated("Bruk fnrBruker")
    @JsonProperty("fodselNrBruker")
    private val oldFnrBruker: String = fnrBruker

    @Deprecated("Bruk eventName")
    @JsonProperty("@event_name")
    private val oldEventName: String = eventName

    constructor(eventName: String, grunnlag: Behovsmeldingsgrunnlag.Digital) : this(
        eventName = eventName,
        søknadId = grunnlag.søknadId,
        fnrBruker = grunnlag.fnrBruker,
        fnrInnsender = grunnlag.fnrInnsender,
        behovsmelding = grunnlag.behovsmelding,
        behovsmeldingGjelder = grunnlag.behovsmeldingGjelder,
    )

    constructor(eventName: String, søknad: SøknadDto) : this(
        eventName = eventName,
        søknadId = søknad.søknadId,
        fnrBruker = søknad.fnrBruker,
        fnrInnsender = søknad.fnrInnsender,
        behovsmelding = jsonMapper.convertValue(søknad.data),
        behovsmeldingGjelder = søknad.søknadGjelder,
    )
}
