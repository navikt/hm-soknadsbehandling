package no.nav.hjelpemidler.soknad.mottak.river

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helse.rapids_rivers.asLocalDate
import no.nav.hjelpemidler.behovsmeldingsmodell.BehovsmeldingStatus
import no.nav.hjelpemidler.behovsmeldingsmodell.BehovsmeldingType
import no.nav.hjelpemidler.soknad.mottak.client.InfotrygdProxyClient
import no.nav.hjelpemidler.soknad.mottak.client.SøknadForRiverClient
import no.nav.hjelpemidler.soknad.mottak.logging.sikkerlogg
import no.nav.hjelpemidler.soknad.mottak.metrics.Prometheus
import no.nav.hjelpemidler.soknad.mottak.service.OrdrelinjeData
import java.util.UUID

private val logger = KotlinLogging.logger {}

class NyInfotrygdOrdrelinje(
    rapidsConnection: RapidsConnection,
    private val søknadForRiverClient: SøknadForRiverClient,
    private val infotrygdProxyClient: InfotrygdProxyClient,
) : AsyncPacketListener {
    init {
        River(rapidsConnection).apply {
            validate { it.demandValue("eventName", "hm-NyOrdrelinje") }
            validate { it.requireKey("eventId", "opprettet", "fnrBruker", "data") }
        }.register(this)
    }

    private val JsonMessage.eventId get() = uuidValue("eventId")
    private val JsonMessage.fnrBruker get() = this["fnrBruker"].textValue()
    private val JsonMessage.oebsId get() = this["data"]["oebsId"].intValue()
    private val JsonMessage.serviceforespørsel get() = this["data"]["serviceforespørsel"].intValue()
    private val JsonMessage.ordrenr get() = this["data"]["ordrenr"].intValue()
    private val JsonMessage.ordrelinje get() = this["data"]["ordrelinje"].intValue()
    private val JsonMessage.delordrelinje get() = this["data"]["delordrelinje"].intValue()
    private val JsonMessage.artikkelnr get() = this["data"]["artikkelnr"].textValue()
    private val JsonMessage.antall get() = this["data"]["antall"].doubleValue()
    private val JsonMessage.enhet get() = this["data"]["enhet"].textValue()
    private val JsonMessage.produktgruppe get() = this["data"]["produktgruppe"].textValue()
    private val JsonMessage.produktgruppeNr get() = this["data"]["produktgruppeNr"].textValue()
    private val JsonMessage.hjelpemiddeltype get() = this["data"]["hjelpemiddeltype"].textValue()
    private val JsonMessage.data get() = this["data"]

    // Kun brukt til Infotrygd-matching for å finne søknadId
    private val JsonMessage.saksblokkOgSaksnr get() = this["data"]["saksblokkOgSaksnr"].textValue()
    private val JsonMessage.vedtaksdato get() = this["data"]["vedtaksdato"].asLocalDate()

    override suspend fun onPacketAsync(packet: JsonMessage, context: MessageContext) {
        val eventId = packet.eventId
        if (packet.saksblokkOgSaksnr.isEmpty()) {
            logger.info { "Skipping illegal event saksblokkOgSaksnr='': $eventId" }
            sikkerlogg.error { "Skippet event med tom saksblokkOgSaksnr: ${packet.toJson()}" }
            return
        }
        if (eventId in skipList) {
            logger.info { "Hopper over event i skipList: $eventId" }
            sikkerlogg.error { "Skippet event: ${packet.toJson()}" }
            return
        }
        try {
            logger.info { "Infotrygd-ordrelinje fra OEBS mottatt med eventId: $eventId" }

            // Match ordrelinje to Infotrygd-table
            val søknadIder = søknadForRiverClient.hentSøknadIdFraVedtaksresultat(
                packet.fnrBruker,
                packet.saksblokkOgSaksnr,
            )

            var søknadId = søknadIder.filter { it.vedtaksDato == packet.vedtaksdato }.map { it.søknadId }.let {
                if (it.count() == 1) {
                    it.first()
                } else {
                    if (it.count() > 1) {
                        sikkerlogg.warn { "Fant flere søknader med matchende fnr+saksblokkOgSaksnr+vedtaksdato (saksblokkOgSaksnr: ${packet.saksblokkOgSaksnr}, vedtaksdato: ${packet.vedtaksdato}, antallTreff: ${it.count()}, id-er: $it)" }
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
                søknadId = søknadIder.filter { it.vedtaksDato == null }.map { it.søknadId }.let {
                    if (it.count() == 1) {
                        it.first()
                    } else {
                        if (it.count() > 1) {
                            logger.info { "Fant flere søknader på bruker som ikke har fått vedtaksdato enda, kan derfor ikke matche til korrekt søknad uten mer informasjon (antall: ${it.count()})" }
                        }
                        null
                    }
                }

                if (søknadId != null) {
                    // Check if we have a decision that is just not synced yet
                    val harVedtakInfotrygd = infotrygdProxyClient.harVedtakFor(
                        packet.fnrBruker,
                        packet.saksblokkOgSaksnr.take(1),
                        packet.saksblokkOgSaksnr.takeLast(2),
                        packet.vedtaksdato
                    )

                    if (harVedtakInfotrygd) {
                        logger.info { "Ordrelinje med eventId: $eventId matchet mot søknad indirekte med sjekk av Infotrygd-databasen (vedtaksdato: ${packet.vedtaksdato}, saksblokkOgSaksnr: ${packet.saksblokkOgSaksnr})" }
                    } else {
                        logger.warn { "Fant en søknadId uten vedtaksdato i databasen for ordrelinje med eventId: $eventId, men fant ikke avgjørelsen i Infotrygd-databasen" }
                        // Do not use it if we do not find a match
                        søknadId = null
                    }
                }

                if (søknadId == null) {
                    logger.warn { "Ordrelinje med eventId: $eventId kan ikke matches mot en søknadId (vedtaksdato: ${packet.vedtaksdato}, saksblokkOgSaksnr: ${packet.saksblokkOgSaksnr})" }
                    return
                }
            }

            val ordrelinjeData = OrdrelinjeData(
                søknadId = søknadId,
                behovsmeldingType = søknadForRiverClient.behovsmeldingTypeFor(søknadId) ?: BehovsmeldingType.SØKNAD,
                oebsId = packet.oebsId,
                fnrBruker = packet.fnrBruker,
                serviceforespørsel = packet.serviceforespørsel,
                ordrenr = packet.ordrenr,
                ordrelinje = packet.ordrelinje,
                delordrelinje = packet.delordrelinje,
                artikkelnr = packet.artikkelnr,
                antall = packet.antall,
                enhet = packet.enhet,
                produktgruppe = packet.produktgruppe,
                produktgruppeNr = packet.produktgruppeNr,
                hjelpemiddeltype = packet.hjelpemiddeltype,
                data = packet.data,
            )

            val ordreSisteDøgn = søknadForRiverClient.ordreSisteDøgn(søknadId = søknadId)
            val result = save(ordrelinjeData)

            if (result == 0) {
                return
            }

            if (!mottokOrdrelinjeFørVedtak) {
                søknadForRiverClient.oppdaterStatus(søknadId, BehovsmeldingStatus.UTSENDING_STARTET)

                if (ordrelinjeData.hjelpemiddeltype == "Del") {
                    logger.info { "Ordrelinje for 'Del' lagret: ${ordrelinjeData.søknadId}" }
                    // Vi skal ikke agere ytterligere på disse
                    return
                }

                if (!ordreSisteDøgn.harOrdreAvTypeHjelpemidler) {
                    context.publish(ordrelinjeData.fnrBruker, ordrelinjeData.toJson("hm-OrdrelinjeLagret"))
                    Prometheus.ordrelinjeVideresendtCounter.inc()
                    logger.info { "Ordrelinje sendt: ${ordrelinjeData.søknadId}" }
                    sikkerlogg.info { "Ordrelinje på bruker: ${ordrelinjeData.søknadId}, fnr: ${ordrelinjeData.fnrBruker})" }
                } else {
                    logger.info { "Ordrelinje mottatt, men varsel til bruker er allerede sendt ut det siste døgnet: $søknadId" }
                }
            } else if (!ordreSisteDøgn.harOrdreAvTypeHjelpemidler) {
                logger.info { "Skippet utsending av sms-varsel for innkommende ordrelinje siden vi mottok ordrelinjen før vedtaket!" }
            }
        } catch (e: Exception) {
            throw RuntimeException("Håndtering av event $eventId feilet", e)
        }
    }

    private suspend fun save(ordrelinje: OrdrelinjeData): Int =
        runCatching {
            søknadForRiverClient.lagreOrdrelinje(ordrelinje)
        }.onSuccess {
            if (it == 0) {
                logger.warn { "Duplikat av ordrelinje for SF: ${ordrelinje.serviceforespørsel}, ordrenr: ${ordrelinje.ordrenr} og ordrelinje/delordrelinje: ${ordrelinje.ordrelinje}/${ordrelinje.delordrelinje} har ikke blitt lagret" }
            } else {
                logger.info { "Lagret ordrelinje for SF: ${ordrelinje.serviceforespørsel}, ordrenr: ${ordrelinje.ordrenr} og ordrelinje/delordrelinje: ${ordrelinje.ordrelinje}/${ordrelinje.delordrelinje}" }
                Prometheus.ordrelinjeLagretCounter.inc()
            }
        }.onFailure {
            logger.error(it) { "Feil under lagring av ordrelinje for SF: ${ordrelinje.serviceforespørsel}, ordrenr: ${ordrelinje.ordrenr} og ordrelinje/delordrelinje: ${ordrelinje.ordrelinje}/${ordrelinje.delordrelinje}" }
        }.getOrThrow()

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
