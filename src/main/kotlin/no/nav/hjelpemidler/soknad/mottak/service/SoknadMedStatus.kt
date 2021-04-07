package no.nav.hjelpemidler.soknad.mottak.service

import com.fasterxml.jackson.databind.JsonNode
import java.util.Date
import java.util.UUID

class SoknadMedStatus private constructor(
    val soknadId: UUID,
    val datoOpprettet: Date,
    val datoOppdatert: Date,
    val status: Status,
    val fullmakt: Boolean,
    val formidlerNavn: String?
) {
    companion object {
        fun newSøknadUtenFormidlernavn(soknadId: UUID, datoOpprettet: Date, datoOppdatert: Date, status: Status, fullmakt: Boolean) =
            SoknadMedStatus(soknadId, datoOpprettet, datoOppdatert, status, fullmakt, null)

        fun newSøknadMedFormidlernavn(soknadId: UUID, datoOpprettet: Date, datoOppdatert: Date, status: Status, fullmakt: Boolean, søknad: JsonNode) =
            SoknadMedStatus(soknadId, datoOpprettet, datoOppdatert, status, fullmakt, formidlerNavn(søknad))
    }
}

private fun formidlerNavn(soknad: JsonNode): String {
    val leveringNode = soknad["soknad"]["levering"]
    return "${leveringNode["hmfFornavn"].textValue()} ${leveringNode["hmfEtternavn"].textValue()}"
}
