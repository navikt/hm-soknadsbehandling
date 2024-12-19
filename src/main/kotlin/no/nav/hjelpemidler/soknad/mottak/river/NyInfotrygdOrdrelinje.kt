package no.nav.hjelpemidler.soknad.mottak.river

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers.asLocalDate
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.hjelpemidler.behovsmeldingsmodell.BehovsmeldingStatus
import no.nav.hjelpemidler.logging.secureLog
import no.nav.hjelpemidler.soknad.mottak.client.InfotrygdProxyClient
import no.nav.hjelpemidler.soknad.mottak.melding.OrdrelinjeLagretMelding
import no.nav.hjelpemidler.soknad.mottak.metrics.Prometheus
import no.nav.hjelpemidler.soknad.mottak.soknadsbehandling.SøknadsbehandlingService
import java.util.UUID

private val log = KotlinLogging.logger {}

class NyInfotrygdOrdrelinje(
    rapidsConnection: RapidsConnection,
    private val søknadsbehandlingService: SøknadsbehandlingService,
    private val infotrygdProxyClient: InfotrygdProxyClient,
) : NyOrdrelinje(), AsyncPacketListener {
    init {
        River(rapidsConnection).apply {
            precondition { it.requireValue("eventName", "hm-NyOrdrelinje") }
            validate { it.requireKey("eventId", "opprettet", "fnrBruker", "data") }
        }.register(this)
    }

    // Kun brukt til Infotrygd-matching for å finne søknadId
    private val JsonMessage.saksblokkOgSaksnr get() = this["data"]["saksblokkOgSaksnr"].textValue()
    private val JsonMessage.vedtaksdato get() = this["data"]["vedtaksdato"].asLocalDate()

    override suspend fun onPacketAsync(packet: JsonMessage, context: MessageContext) {
        val eventId = packet.eventId
        val saksblokkOgSaksnr = packet.saksblokkOgSaksnr
        if (saksblokkOgSaksnr.isEmpty()) {
            log.info { "Hopper over event med ugyldig saksblokkOgSaksnr = '', eventId: $eventId" }
            secureLog.error { "Hopper over event med ugyldig saksblokkOgSaksnr = '', eventId: $eventId, packet: '${packet.toJson()}'" }
            return
        }
        if (eventId in skipList) {
            log.info { "Hopper over event i skipList: $eventId" }
            secureLog.error { "Skippet event: ${packet.toJson()}" }
            return
        }
        try {
            log.info { "Infotrygd-ordrelinje fra OEBS mottatt med eventId: $eventId" }

            // Match ordrelinje to Infotrygd-table
            val fnrBruker = packet.fnrBruker
            val søknadIder = søknadsbehandlingService.hentSøknadIdFraVedtaksresultat(fnrBruker, saksblokkOgSaksnr)
            val vedtaksdato = packet.vedtaksdato
            var søknadId = søknadIder
                .filter { it.vedtaksdato == vedtaksdato }
                .map { it.søknadId }
                .let {
                    if (it.count() == 1) {
                        it.first()
                    } else {
                        if (it.count() > 1) {
                            secureLog.warn { "Fant flere søknader med matchende fnr+saksblokkOgSaksnr+vedtaksdato (saksblokkOgSaksnr: $saksblokkOgSaksnr, vedtaksdato: $vedtaksdato, antallTreff: ${it.count()}, id-er: $it)" }
                        }
                        null
                    }
                }

            var mottokOrdrelinjeFørVedtak = false
            if (søknadId == null) {
                /*
                    If we don't already have the required "vedtak" (decision) stored in our database so that we can
                    match a "søknadId" to our incoming order line, then the likelihood is that it is being held back
                    by hm-infotrygd-poller still because we are receiving the order line on the same day the decision
                    was made on. So now we either have to throw the order line away, or match it somehow without the
                    decision date helping with uniqueness. Here we do assume a match if we both have a reference in
                    our database that is still missing its decision date AND there is a decision with the correct
                    date in the Infotrygd-database. If not we throw the order line away.
                 */

                mottokOrdrelinjeFørVedtak = true

                // Check if we have one and only one application waiting for its decision
                søknadId = søknadIder
                    .filter { it.vedtaksdato == null }
                    .map { it.søknadId }
                    .let {
                        if (it.count() == 1) {
                            it.first()
                        } else {
                            if (it.count() > 1) {
                                log.info { "Fant flere søknader på bruker som ikke har fått vedtaksdato enda, kan derfor ikke matche til korrekt søknad uten mer informasjon (antall: ${it.count()})" }
                            }
                            null
                        }
                    }

                if (søknadId != null) {
                    // Check if we have a decision that is just not synced yet
                    val harVedtakInfotrygd = infotrygdProxyClient.harVedtakFor(
                        fnrBruker,
                        saksblokkOgSaksnr.take(1),
                        saksblokkOgSaksnr.takeLast(2),
                        vedtaksdato
                    )

                    if (harVedtakInfotrygd) {
                        log.info { "Ordrelinje med eventId: $eventId matchet indirekte mot søknad med sjekk av Infotrygd-databasen (vedtaksdato: $vedtaksdato, saksblokkOgSaksnr: $saksblokkOgSaksnr)" }
                    } else {
                        log.warn { "Fant en søknadId uten vedtaksdato i databasen for ordrelinje med eventId: $eventId, men fant ikke avgjørelsen i Infotrygd-databasen" }
                        // Do not use it if we do not find a match
                        søknadId = null
                    }
                }

                if (søknadId == null) {
                    log.warn { "Ordrelinje med eventId: $eventId kan ikke matches mot en søknadId (vedtaksdato: $vedtaksdato, saksblokkOgSaksnr: $saksblokkOgSaksnr)" }
                    return
                }
            }

            val innkommendeOrdrelinje = packet.innkommendeOrdrelinje
            val ordrelinje = innkommendeOrdrelinje.tilOrdrelinje(søknadId, fnrBruker, packet.data)

            val lagret = søknadsbehandlingService.lagreOrdrelinje(ordrelinje)
            if (!lagret) {
                return
            }

            val ordreSisteDøgn = søknadsbehandlingService.ordreSisteDøgn(søknadId)
            if (!mottokOrdrelinjeFørVedtak) {
                søknadsbehandlingService.oppdaterStatus(søknadId, BehovsmeldingStatus.UTSENDING_STARTET)

                if (ordrelinje.forDel) {
                    log.info { "Ordrelinje for 'Del' lagret: $søknadId" }
                    // Vi skal ikke agere ytterligere på disse
                    return
                }

                if (!ordreSisteDøgn.harOrdreAvTypeHjelpemidler) {
                    val behovsmeldingType = søknadsbehandlingService.hentBehovsmeldingstype(søknadId)
                    context.publish(fnrBruker, OrdrelinjeLagretMelding(ordrelinje, behovsmeldingType))
                    Prometheus.ordrelinjeVideresendtCounter.increment()
                    log.info { "Ordrelinje sendt, søknadId: $søknadId" }
                    secureLog.info { "Ordrelinje sendt, søknadId: $søknadId, fnrBruker: $fnrBruker" }
                } else {
                    log.info { "Ordrelinje mottatt, men varsel til bruker er allerede sendt ut det siste døgnet: $søknadId" }
                }
            } else if (!ordreSisteDøgn.harOrdreAvTypeHjelpemidler) {
                log.info { "Skippet utsending av SMS-varsel for innkommende ordrelinje siden vi mottok ordrelinjen før vedtaket" }
            }
        } catch (e: Exception) {
            log.error(e) { "Håndtering av event $eventId feilet" }
            throw e
        }
    }

    private val skipList = listOf(
        "93cd36de-feab-4a29-8aa3-4ef976d203ef",
        "1e4690d3-72c5-4cbe-96be-34bdbf3a3022",
        "60e9a574-15ea-4f3c-befb-6fdb4341e450",
        "38ef8296-2445-420d-ac72-6749982cfb34",
        "05b03961-fe85-44df-8af7-7528c57deed9",
        "682e025b-9f01-4976-8fe4-08940522e327",
        "13c9b843-aafb-4da5-9ed1-c86fdc578f18",
        "b9235bf1-8b29-4bef-a3ea-c191c344afd1",
        "a8aa7b63-9457-4e5d-b77b-1767fac96fb3",
    ).map(UUID::fromString)
}
