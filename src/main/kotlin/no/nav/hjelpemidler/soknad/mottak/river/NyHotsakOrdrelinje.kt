package no.nav.hjelpemidler.soknad.mottak.river

import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.hjelpemidler.soknad.mottak.client.SøknadForRiverClient
import no.nav.hjelpemidler.soknad.mottak.metrics.Prometheus
import no.nav.hjelpemidler.soknad.mottak.service.OrdrelinjeData
import no.nav.hjelpemidler.soknad.mottak.service.Status
import java.util.UUID

private val logger = KotlinLogging.logger {}
private val sikkerlogg = KotlinLogging.logger("tjenestekall")

internal class NyHotsakOrdrelinje(
    rapidsConnection: RapidsConnection,
    private val søknadForRiverClient: SøknadForRiverClient
) : PacketListenerWithOnError {

    init {
        River(rapidsConnection).apply {
            validate { it.demandValue("eventName", "hm-NyOrdrelinje-hotsak") }
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
    private val JsonMessage.hjelpemiddeltype get() = this["data"]["hjelpemiddeltype"].textValue()
    private val JsonMessage.data get() = this["data"]

    private val JsonMessage.saksnummer get() = this["data"]["saksnummer"].textValue()

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        runBlocking {
            if (packet.saksnummer.isEmpty()) {
                logger.info("Skipping illegal event HOTSAK saksnummer='': ${packet.eventId}")
                sikkerlogg.error("Skippet event med tomt  HOTSAK saksnumer: ${packet.toJson()}")
                return@runBlocking
            }
            if (skipEvent(UUID.fromString(packet.eventId))) {
                logger.info("Hopper over event i skip-list: ${packet.eventId}")
                sikkerlogg.error("Skippet event: ${packet.toJson()}")
                return@runBlocking
            }
            try {
                logger.info { "Hotsak ordrelinje fra Oebs mottatt med eventId: ${packet.eventId}" }

                // Match ordrelinje to Hotsak-table
                val søknadId = søknadForRiverClient.hentSøknadIdFraHotsakSaksnummer(
                    packet.saksnummer,
                )
                if (søknadId == null) {
                    logger.warn { "Ordrelinje med eventId ${packet.eventId} og saksnummer ${packet.saksnummer} kan ikke matches mot en søknadId" }
                    return@runBlocking
                }

                logger.info("Fant søknadsid $søknadId fra HOTSAK saksnummer ${packet.saksnummer}")

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
                    hjelpemiddeltype = packet.hjelpemiddeltype,
                    data = packet.data,
                )

                val ordreSisteDøgn = søknadForRiverClient.ordreSisteDøgn(soknadsId = søknadId)
                val result = save(ordrelinjeData)

                if (result == 0) {
                    return@runBlocking
                }

                søknadForRiverClient.oppdaterStatus(søknadId, Status.UTSENDING_STARTET)

                if (ordrelinjeData.hjelpemiddeltype == "Del") {
                    logger.info("Ordrelinje for 'Del' lagret: ${ordrelinjeData.søknadId}")
                    // Vi skal ikke agere ytterligere på disse
                    return@runBlocking
                }

                if (!ordreSisteDøgn.harOrdreAvTypeHjelpemidler) {
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

    private fun skipEvent(eventId: UUID): Boolean {
        val skipList = mutableListOf<UUID>()
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
