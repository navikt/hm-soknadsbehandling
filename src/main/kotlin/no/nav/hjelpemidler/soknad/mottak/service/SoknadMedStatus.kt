package no.nav.hjelpemidler.soknad.mottak.service

import com.fasterxml.jackson.databind.JsonNode
import java.util.Date
import java.util.UUID

class SoknadMedStatus private constructor(
    val soknadId: UUID,
    val datoOpprettet: Date,
    val datoOppdatert: Date,
    val status: Status,
    val formidlerNavn: String?
) {
    companion object {
        fun newSøknadUtenFormidlernavn(soknadId: UUID, datoOpprettet: Date, datoOppdatert: Date, status: Status) =
            SoknadMedStatus(soknadId, datoOpprettet, datoOppdatert, status, null)

        fun newSøknadMedFormidlernavn(soknadId: UUID, datoOpprettet: Date, datoOppdatert: Date, status: Status, søknad: JsonNode) =
            SoknadMedStatus(soknadId, datoOpprettet, datoOppdatert, status, formidlerNavn(søknad))
    }
}

private fun formidlerNavn(soknad: JsonNode): String {
    val leveringNode = soknad["soknad"]["levering"]
    return "${leveringNode["hmfFornavn"].textValue()} ${leveringNode["hmfEtternavn"].textValue()}"
}
