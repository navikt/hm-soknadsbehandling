package no.nav.hjelpemidler.soknad.mottak.service

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageProblems
import no.nav.hjelpemidler.behovsmeldingsmodell.SøknadId
import no.nav.hjelpemidler.behovsmeldingsmodell.TilknyttetSøknad
import no.nav.hjelpemidler.behovsmeldingsmodell.sak.InfotrygdSakId
import java.time.LocalDate

data class VedtaksresultatData(
    override val søknadId: SøknadId,
    val fnrBruker: String,
    val trygdekontorNr: String?,
    val saksblokk: String?,
    val saksnr: String?,
    val vedtaksresultat: String? = null,
    val vedtaksdato: LocalDate? = null,
) : TilknyttetSøknad {
    constructor(søknadId: SøknadId, fnrBruker: String, fagsakId: InfotrygdSakId) : this(
        søknadId = søknadId,
        fnrBruker = fnrBruker,
        trygdekontorNr = fagsakId.trygdekontornummer,
        saksblokk = fagsakId.saksblokk,
        saksnr = fagsakId.saksnummer,
    )

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
}
