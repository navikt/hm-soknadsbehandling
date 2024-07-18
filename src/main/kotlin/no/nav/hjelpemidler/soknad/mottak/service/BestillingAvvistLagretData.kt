package no.nav.hjelpemidler.soknad.mottak.service

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageProblems
import java.time.LocalDateTime
import java.util.UUID

internal data class BestillingAvvistLagretData(
    val søknadId: UUID,
    val fnrBruker: String,
    val opprettet: LocalDateTime,
    val begrunnelse: String,
    val valgteÅrsaker: List<String>,
) {
    internal fun toJson(eventName: String): String {
        return JsonMessage("{}", MessageProblems("")).also {
            it["eventName"] = eventName
            it["eventId"] = UUID.randomUUID()
            it["søknadId"] = this.søknadId
            it["fnrBruker"] = this.fnrBruker
            it["opprettet"] = this.opprettet
            it["begrunnelse"] = this.begrunnelse
            it["valgteÅrsaker"] = this.valgteÅrsaker
        }.toJson()
    }
}
