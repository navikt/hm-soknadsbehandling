package no.nav.hjelpemidler.soknad.mottak.service

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.convertValue
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.hjelpemidler.behovsmeldingsmodell.BehovsmeldingStatus
import no.nav.hjelpemidler.behovsmeldingsmodell.Behovsmeldingsgrunnlag
import no.nav.hjelpemidler.behovsmeldingsmodell.SøknadId
import no.nav.hjelpemidler.behovsmeldingsmodell.TilknyttetSøknad
import no.nav.hjelpemidler.soknad.mottak.client.Søknad
import no.nav.hjelpemidler.soknad.mottak.jsonMapper
import no.nav.hjelpemidler.soknad.mottak.river.Melding
import no.nav.hjelpemidler.soknad.mottak.river.jsonMessageOf
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
) : TilknyttetSøknad, Melding {
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

    fun toGrunnlag() = Behovsmeldingsgrunnlag.Digital(
        søknadId = soknadId,
        status = status,
        fnrBruker = fnrBruker,
        navnBruker = navnBruker,
        fnrInnsender = fnrInnsender,
        kommunenavn = kommunenavn,
        behovsmelding = jsonMapper.convertValue(soknad),
        behovsmeldingGjelder = soknadGjelder,
    )

    override fun toJsonMessage(eventName: String): JsonMessage {
        val id = UUID.randomUUID()
        return jsonMessageOf(
            "@id" to id, // @deprecated
            "eventId" to id,
            "@event_name" to eventName, // @deprecated
            "eventName" to eventName,
            "opprettet" to LocalDateTime.now(),
            "fodselNrBruker" to fnrBruker, // @deprecated
            "fnrBruker" to fnrBruker,
            "soknad" to soknad,
            "soknadId" to soknadId,
            "fnrInnsender" to fnrInnsender,
            "soknadGjelder" to soknadGjelder,
        )
    }

    fun toVenterPåGodkjenningJson(): String {
        val id = UUID.randomUUID()
        return jsonMessageOf(
            "@id" to id, // @deprecated
            "eventId" to id,
            "@event_name" to "SøknadTilGodkjenning", // @deprecated
            "eventName" to "hm-SøknadTilGodkjenning",
            "opprettet" to LocalDateTime.now(),
            "fodselNrBruker" to this.fnrBruker, // @deprecated
            "fnrBruker" to this.fnrBruker,
            "soknadId" to this.soknadId,
            "kommunenavn" to (this.kommunenavn ?: ""),
        ).toJson()
    }
}
