package no.nav.hjelpemidler.soknad.mottak.service

import com.fasterxml.jackson.databind.JsonNode
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.hjelpemidler.behovsmeldingsmodell.BehovsmeldingType
import no.nav.hjelpemidler.behovsmeldingsmodell.SøknadId
import no.nav.hjelpemidler.behovsmeldingsmodell.TilknyttetSøknad
import no.nav.hjelpemidler.soknad.mottak.river.Melding
import no.nav.hjelpemidler.soknad.mottak.river.jsonMessageOf
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
) : TilknyttetSøknad, Melding {
    override fun toJsonMessage(eventName: String): JsonMessage = jsonMessageOf(
        "eventName" to eventName,
        "eventId" to UUID.randomUUID(),
        "søknadId" to søknadId,
        "fnrBruker" to fnrBruker,
        "behovsmeldingType" to behovsmeldingType,
    )
}
