package no.nav.hjelpemidler.soknad.mottak.service

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageProblems
import java.util.UUID

internal data class VedtaksresultatLagretData(
    val søknadId: UUID,
    val fnrBruker: String,
    val vedtaksresultat: String,
) {
    internal fun toJson(eventName: String): String {
        return JsonMessage("{}", MessageProblems("")).also {
            it["eventName"] = eventName
            it["søknadId"] = this.søknadId
            it["fnrBruker"] = this.fnrBruker
            it["vedtaksresultat"] = this.vedtaksresultat
        }.toJson()
    }
}
