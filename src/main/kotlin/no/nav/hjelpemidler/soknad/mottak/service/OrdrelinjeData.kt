package no.nav.hjelpemidler.soknad.mottak.service

import com.fasterxml.jackson.databind.JsonNode
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageProblems
import no.nav.hjelpemidler.behovsmeldingsmodell.BehovsmeldingType
import no.nav.hjelpemidler.behovsmeldingsmodell.SøknadId
import no.nav.hjelpemidler.behovsmeldingsmodell.TilknyttetSøknad
import java.util.UUID

data class OrdrelinjeData(
    override val søknadId: SøknadId,
    val behovsmeldingType: BehovsmeldingType,
    val oebsId: Int,
    val fnrBruker: String,
    val serviceforespørsel: Int?, // Viss det ikkje er ein SF
    val ordrenr: Int,
    val ordrelinje: Int,
    val delordrelinje: Int,
    val artikkelnr: String,
    val antall: Double,
    val enhet: String,
    val produktgruppe: String,
    val produktgruppeNr: String,
    val hjelpemiddeltype: String,
    val data: JsonNode?,
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
