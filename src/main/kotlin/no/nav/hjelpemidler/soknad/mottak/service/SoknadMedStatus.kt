package no.nav.hjelpemidler.soknad.mottak.service

import com.fasterxml.jackson.databind.JsonNode
import java.util.Date
import java.util.UUID

class SoknadMedStatus(
    val soknadId: UUID,
    val datoOpprettet: Date,
    val datoOppdatert: Date,
    val status: Status,
    soknad: JsonNode,
) {
    val formidlerNavn = formidlerNavn(soknad)
}

private fun formidlerNavn(soknad: JsonNode): String {
    val leveringNode = soknad["soknad"]["levering"]
    return "${leveringNode["hmfFornavn"].textValue()} ${leveringNode["hmfEtternavn"].textValue()}"
}
