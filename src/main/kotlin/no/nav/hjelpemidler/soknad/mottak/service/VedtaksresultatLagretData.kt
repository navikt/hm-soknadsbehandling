package no.nav.hjelpemidler.soknad.mottak.service

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageProblems
import no.nav.hjelpemidler.behovsmeldingsmodell.SøknadId
import no.nav.hjelpemidler.behovsmeldingsmodell.TilknyttetSøknad
import java.time.LocalDateTime
import java.util.UUID

private val log = KotlinLogging.logger {}

data class VedtaksresultatLagretData(
    override val søknadId: SøknadId,
    val fnrBruker: String,
    val vedtaksdato: LocalDateTime,
    val vedtaksresultat: String,
    val eksternVarslingDeaktivert: Boolean = false,
) : TilknyttetSøknad {
    fun toJson(eventName: String, søknadstype: String?): String {
        return JsonMessage("{}", MessageProblems("")).also {
            it["eventName"] = eventName
            it["eventId"] = UUID.randomUUID()
            it["søknadId"] = this.søknadId
            it["fnrBruker"] = this.fnrBruker
            it["vedtaksdato"] = this.vedtaksdato
            it["vedtaksresultat"] = this.vedtaksresultat
            it["eksternVarslingDeaktivert"] = this.eksternVarslingDeaktivert
            if (søknadstype != null) {
                log.debug { "Sender vedtak til Ditt NAV med søknadstype: $søknadstype" }
                it["søknadsType"] = søknadstype
            }
        }.toJson()
    }
}
