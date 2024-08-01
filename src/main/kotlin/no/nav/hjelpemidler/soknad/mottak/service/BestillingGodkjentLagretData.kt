package no.nav.hjelpemidler.soknad.mottak.service

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageProblems
import no.nav.hjelpemidler.behovsmeldingsmodell.TilknyttetSøknad
import java.time.LocalDateTime
import java.util.UUID

data class BestillingGodkjentLagretData(
    override val søknadId: UUID,
    val fnrBruker: String,
    val opprettet: LocalDateTime,
) : TilknyttetSøknad {
    fun toJson(eventName: String): String {
        return JsonMessage("{}", MessageProblems("")).also {
            it["eventName"] = eventName
            it["eventId"] = UUID.randomUUID()
            it["søknadId"] = this.søknadId
            it["fnrBruker"] = this.fnrBruker
            it["opprettet"] = this.opprettet
        }.toJson()
    }
}
