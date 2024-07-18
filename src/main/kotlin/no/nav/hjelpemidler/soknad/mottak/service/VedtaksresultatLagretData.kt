package no.nav.hjelpemidler.soknad.mottak.service

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageProblems
import java.time.LocalDateTime
import java.util.UUID

private val logg = KotlinLogging.logger {}

internal data class VedtaksresultatLagretData(
    val søknadId: UUID,
    val fnrBruker: String,
    val vedtaksdato: LocalDateTime,
    val vedtaksresultat: String,
    val eksternVarslingDeaktivert: Boolean = false,
) {
    internal fun toJson(eventName: String, søknadsType: String?): String {
        return JsonMessage("{}", MessageProblems("")).also {
            it["eventName"] = eventName
            it["eventId"] = UUID.randomUUID()
            it["søknadId"] = this.søknadId
            it["fnrBruker"] = this.fnrBruker
            it["vedtaksdato"] = this.vedtaksdato
            it["vedtaksresultat"] = this.vedtaksresultat
            it["eksternVarslingDeaktivert"] = this.eksternVarslingDeaktivert
            søknadsType?.let { st ->
                logg.info { "DEBUG: sender vedtak til ditt-nav med søknadsType=$st" }
                it["søknadsType"] = st
            }
        }.toJson()
    }
}
