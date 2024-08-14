package no.nav.hjelpemidler.soknad.mottak.service

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.hjelpemidler.behovsmeldingsmodell.SøknadId
import no.nav.hjelpemidler.behovsmeldingsmodell.TilknyttetSøknad
import no.nav.hjelpemidler.behovsmeldingsmodell.sak.InfotrygdSakId
import no.nav.hjelpemidler.soknad.mottak.river.Melding
import no.nav.hjelpemidler.soknad.mottak.river.jsonMessageOf
import java.time.LocalDate

data class VedtaksresultatData(
    override val søknadId: SøknadId,
    val fnrBruker: String,
    val trygdekontorNr: String?,
    val saksblokk: String?,
    val saksnr: String?,
    val vedtaksresultat: String? = null,
    val vedtaksdato: LocalDate? = null,
) : TilknyttetSøknad, Melding {
    constructor(søknadId: SøknadId, fnrBruker: String, fagsakId: InfotrygdSakId) : this(
        søknadId = søknadId,
        fnrBruker = fnrBruker,
        trygdekontorNr = fagsakId.trygdekontornummer,
        saksblokk = fagsakId.saksblokk,
        saksnr = fagsakId.saksnummer,
    )

    override fun toJsonMessage(eventName: String): JsonMessage = jsonMessageOf(
        "eventName" to eventName,
        "søknadId" to søknadId,
        "fnrBruker" to fnrBruker,
        "trygdekontorNr" to trygdekontorNr,
        "saksblokk" to saksblokk,
        "saksnr" to saksnr,
    )
}
