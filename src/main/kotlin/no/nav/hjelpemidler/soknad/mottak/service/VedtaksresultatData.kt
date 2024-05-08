package no.nav.hjelpemidler.soknad.mottak.service

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageProblems
import java.time.LocalDate
import java.util.UUID

data class VedtaksresultatData(
    val søknadId: UUID,
    val fnrBruker: String,
    val trygdekontorNr: String?,
    val saksblokk: String?,
    val saksnr: String?,
    val vedtaksresultat: String? = null,
    val vedtaksdato: LocalDate? = null,
) {
    fun toJson(eventName: String): String {
        return JsonMessage("{}", MessageProblems("")).also {
            it["eventName"] = eventName
            it["søknadId"] = this.søknadId
            it["fnrBruker"] = this.fnrBruker
            it["trygdekontorNr"] = this.trygdekontorNr!!
            it["saksblokk"] = this.saksblokk!!
            it["saksnr"] = this.saksnr!!
        }.toJson()
    }

    companion object {
        fun getTrygdekontorNrFromFagsakId(fagsakId: String): String {
            return fagsakId.take(4)
        }

        fun getSaksblokkFromFagsakId(fagsakId: String): String {
            val saksblokkOgSaksnummer = fagsakId.takeLast(3)
            return saksblokkOgSaksnummer.first().toString()
        }

        fun getSaksnrFromFagsakId(fagsakId: String): String {
            val saksblokkOgSaksnummer = fagsakId.takeLast(3)
            return saksblokkOgSaksnummer.takeLast(2)
        }
    }
}
