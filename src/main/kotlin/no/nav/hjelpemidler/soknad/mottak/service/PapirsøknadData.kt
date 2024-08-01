package no.nav.hjelpemidler.soknad.mottak.service

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageProblems
import no.nav.hjelpemidler.behovsmeldingsmodell.BehovsmeldingStatus
import no.nav.hjelpemidler.behovsmeldingsmodell.SøknadId
import java.time.LocalDateTime
import java.util.UUID

data class PapirsøknadData(
    val fnrBruker: String,
    override val søknadId: SøknadId,
    val status: BehovsmeldingStatus,
    val journalpostId: String,
    val navnBruker: String,
) : BehovsmeldingGrunnlag {
    fun toJson(eventName: String): String {
        return JsonMessage("{}", MessageProblems("")).also {
            it["eventId"] = UUID.randomUUID()
            it["eventName"] = eventName
            it["opprettet"] = LocalDateTime.now()
            it["fnrBruker"] = this.fnrBruker
            it["journalpostId"] = this.journalpostId
            it["soknadId"] = this.søknadId
        }.toJson()
    }
}
