package no.nav.hjelpemidler.soknad.mottak.service

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageProblems
import no.nav.hjelpemidler.behovsmeldingsmodell.BehovsmeldingType
import no.nav.hjelpemidler.behovsmeldingsmodell.TilknyttetSøknad
import java.util.UUID

data class SøknadUnderBehandlingData(
    override val søknadId: UUID,
    val fnrBruker: String,
    val behovsmeldingType: BehovsmeldingType,
) : TilknyttetSøknad {
    fun toJson(eventName: String): String {
        return JsonMessage("{}", MessageProblems("")).also {
            it["eventName"] = eventName
            it["eventId"] = UUID.randomUUID()
            it["søknadId"] = this.søknadId
            it["fnrBruker"] = this.fnrBruker
            it["behovsmeldingType"] = this.behovsmeldingType
        }.toJson()
    }
}
