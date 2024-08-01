package no.nav.hjelpemidler.soknad.mottak.service

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.databind.JsonNode
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageProblems
import no.nav.hjelpemidler.behovsmeldingsmodell.BehovsmeldingStatus
import no.nav.hjelpemidler.behovsmeldingsmodell.SøknadId
import no.nav.hjelpemidler.soknad.mottak.client.Søknad
import java.time.LocalDateTime
import java.util.UUID

data class SøknadData(
    val fnrBruker: String,
    val navnBruker: String, // Lagres til hm-soknadsbehandling-db, slik at det kan vises i hm-formidler, selv om bruker sletter søknaden (brukerbekreftelse)
    val fnrInnsender: String,
    val soknadId: SøknadId,
    val soknad: JsonNode,
    val status: BehovsmeldingStatus,
    val kommunenavn: String?,
    val soknadGjelder: String?,
) : BehovsmeldingGrunnlag {
    override val søknadId: SøknadId @JsonIgnore get() = soknadId

    constructor(søknad: Søknad) : this(
        fnrBruker = søknad.fnrBruker,
        navnBruker = søknad.navnBruker,
        fnrInnsender = requireNotNull(søknad.fnrInnsender) { "fnrInnsender mangler" },
        soknadId = søknad.søknadId,
        soknad = søknad.data,
        status = søknad.status,
        kommunenavn = søknad.kommunenavn,
        soknadGjelder = søknad.søknadGjelder,
    )

    fun toJson(eventName: String): String {
        return JsonMessage("{}", MessageProblems("")).also {
            val id = UUID.randomUUID()
            it["@id"] = id // @deprecated
            it["eventId"] = id
            it["@event_name"] = eventName // @deprecated
            it["eventName"] = eventName
            it["opprettet"] = LocalDateTime.now()
            it["fodselNrBruker"] = this.fnrBruker // @deprecated
            it["fnrBruker"] = this.fnrBruker
            it["soknad"] = this.soknad
            it["soknadId"] = this.soknadId
            it["fnrInnsender"] = this.fnrInnsender
            if (this.soknadGjelder != null) it["soknadGjelder"] = this.soknadGjelder
        }.toJson()
    }

    fun toVenterPåGodkjenningJson(): String {
        return JsonMessage("{}", MessageProblems("")).also {
            val id = UUID.randomUUID()
            it["@id"] = id // @deprecated
            it["eventId"] = id
            it["@event_name"] = "SøknadTilGodkjenning" // @deprecated
            it["eventName"] = "hm-SøknadTilGodkjenning"
            it["opprettet"] = LocalDateTime.now()
            it["fodselNrBruker"] = this.fnrBruker // @deprecated
            it["fnrBruker"] = this.fnrBruker
            it["soknadId"] = this.soknadId
            it["kommunenavn"] = this.kommunenavn ?: ""
        }.toJson()
    }
}
