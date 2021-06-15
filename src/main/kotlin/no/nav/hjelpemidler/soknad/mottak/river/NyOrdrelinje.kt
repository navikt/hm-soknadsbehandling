package no.nav.hjelpemidler.soknad.mottak.river

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helse.rapids_rivers.asLocalDate
import no.nav.hjelpemidler.soknad.mottak.client.SøknadForRiverClient
import no.nav.hjelpemidler.soknad.mottak.metrics.Prometheus
import no.nav.hjelpemidler.soknad.mottak.service.OrdrelinjeData
import no.nav.hjelpemidler.soknad.mottak.service.Status
import java.util.UUID

private val logger = KotlinLogging.logger {}
private val sikkerlogg = KotlinLogging.logger("tjenestekall")

internal class NyOrdrelinje(
    rapidsConnection: RapidsConnection,
    private val søknadForRiverClient: SøknadForRiverClient
) : PacketListenerWithOnError {

    init {
        River(rapidsConnection).apply {
            validate { it.demandValue("eventName", "hm-NyOrdrelinje") }
            validate { it.requireKey("eventId", "opprettet", "fnrBruker", "data") }
        }.register(this)
    }

    private val JsonMessage.eventId get() = this["eventId"].textValue()
    private val JsonMessage.opprettet get() = this["opprettet"].textValue()
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
    private val JsonMessage.data get() = this["data"]

    // Kun brukt til Infotrygd-matching for å finne søknadId
    private val JsonMessage.saksblokkOgSaksnr get() = this["data"]["saksblokkOgSaksnr"].textValue()
    private val JsonMessage.vedtaksdato get() = this["data"]["vedtaksdato"].asLocalDate()

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        runBlocking {
            withContext(Dispatchers.IO) {
                launch {
                    if (packet.saksblokkOgSaksnr.isEmpty()) {
                        logger.info("Skipping illegal event saksblokkOgSaksnr='': ${packet.eventId}")
                        sikkerlogg.error("Skippet event med tom saksblokkOgSaksnr: ${packet.toJson()}")
                        return@launch
                    }
                    if (skipEvent(UUID.fromString(packet.eventId))) {
                        logger.info("Hopper over event i skip-list: ${packet.eventId}")
                        sikkerlogg.error("Skippet event: ${packet.toJson()}")
                        return@launch
                    }
                    try {
                        logger.info { "Ordrelinje fra Oebs mottatt med eventId: ${packet.eventId}" }

                        // Match ordrelinje to Infotrygd-table
                        val søknadId = søknadForRiverClient.hentSøknadIdFraVedtaksresultat(
                            packet.fnrBruker,
                            packet.saksblokkOgSaksnr,
                            packet.vedtaksdato
                        )
                        if (søknadId == null) {
                            logger.warn { "Ordrelinje med eventId ${packet.eventId} kan ikkje matchast mot ein søknadId" }
                            return@launch
                        }

                        logger.info("NOTE NOTE NOTE: IGNORING oebsId (set to -1), enhet (set to \"STK\"), and produktgruppeNr (set to \"\") temporarily: ${packet.eventId}")

                        val ordrelinjeData = OrdrelinjeData(
                            søknadId = søknadId,
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
                            data = packet.data,
                        )

                        val ordreSisteDøgn = søknadForRiverClient.ordreSisteDøgn(soknadsId = søknadId)
                        val result = save(ordrelinjeData)

                        if (result == 0) {
                            return@launch
                        }

                        søknadForRiverClient.oppdaterStatus(søknadId, Status.UTSENDING_STARTET)

                        if (!ordreSisteDøgn) {
                            context.publish(ordrelinjeData.fnrBruker, ordrelinjeData.toJson("hm-OrdrelinjeLagret"))
                            Prometheus.ordrelinjeLagretOgSendtTilRapidCounter.inc()
                            logger.info("Ordrelinje sendt: ${ordrelinjeData.søknadId}")
                            sikkerlogg.info("Ordrelinje på bruker: ${ordrelinjeData.søknadId}, fnr: ${ordrelinjeData.fnrBruker})")
                        } else {
                            logger.info("Ordrelinje mottatt, men varsel til bruker er allerede sendt ut det siste døgnet: $søknadId")
                        }
                    } catch (e: Exception) {
                        throw RuntimeException("Håndtering av event ${packet.eventId} feilet", e)
                    }
                }
            }
        }
    }

    private fun skipEvent(eventId: UUID): Boolean {
        val skipList = mutableListOf<UUID>()
        skipList.add(UUID.fromString("93cd36de-feab-4a29-8aa3-4ef976d203ef"))
        skipList.add(UUID.fromString("1e4690d3-72c5-4cbe-96be-34bdbf3a3022"))
        skipList.add(UUID.fromString("60e9a574-15ea-4f3c-befb-6fdb4341e450"))
        skipList.add(UUID.fromString("38ef8296-2445-420d-ac72-6749982cfb34"))
        skipList.add(UUID.fromString("05b03961-fe85-44df-8af7-7528c57deed9"))
        skipList.add(UUID.fromString("682e025b-9f01-4976-8fe4-08940522e327"))
        skipList.add(UUID.fromString("13c9b843-aafb-4da5-9ed1-c86fdc578f18"))
        skipList.add(UUID.fromString("b9235bf1-8b29-4bef-a3ea-c191c344afd1"))
        skipList.add(UUID.fromString("a8aa7b63-9457-4e5d-b77b-1767fac96fb3"))
        return skipList.any { it == eventId }
    }

    private suspend fun save(ordrelinje: OrdrelinjeData): Int =
        kotlin.runCatching {
            søknadForRiverClient.save(ordrelinje)
        }.onSuccess {
            if (it == 0) {
                logger.warn("Duplikat av ordrelinje for SF ${ordrelinje.serviceforespørsel}, ordrenr ${ordrelinje.ordrenr} og ordrelinje/delordrelinje ${ordrelinje.ordrelinje}/${ordrelinje.delordrelinje} har ikkje blitt lagra")
            } else {
                logger.info("Lagra ordrelinje for SF ${ordrelinje.serviceforespørsel}, ordrenr ${ordrelinje.ordrenr} og ordrelinje/delordrelinje ${ordrelinje.ordrelinje}/${ordrelinje.delordrelinje}")
                Prometheus.ordrelinjeLagretCounter.inc()
            }
        }.onFailure {
            logger.error(it) { "Feil under lagring av ordrelinje for SF ${ordrelinje.serviceforespørsel}, ordrenr ${ordrelinje.ordrenr} og ordrelinje/delordrelinje ${ordrelinje.ordrelinje}/${ordrelinje.delordrelinje}" }
        }.getOrThrow()
}
