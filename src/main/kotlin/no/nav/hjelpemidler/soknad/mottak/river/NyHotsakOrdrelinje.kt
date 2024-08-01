package no.nav.hjelpemidler.soknad.mottak.river

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.hjelpemidler.behovsmeldingsmodell.BehovsmeldingStatus
import no.nav.hjelpemidler.behovsmeldingsmodell.sak.HotsakSakId
import no.nav.hjelpemidler.soknad.mottak.asObject
import no.nav.hjelpemidler.soknad.mottak.client.SøknadForRiverClient
import no.nav.hjelpemidler.soknad.mottak.metrics.Prometheus
import no.nav.hjelpemidler.soknad.mottak.publish
import no.nav.hjelpemidler.soknad.mottak.service.OrdrelinjeData
import java.util.UUID

private val logger = KotlinLogging.logger {}
private val sikkerlogg = KotlinLogging.logger("tjenestekall")

class NyHotsakOrdrelinje(
    rapidsConnection: RapidsConnection,
    private val søknadForRiverClient: SøknadForRiverClient,
) : AsyncPacketListener {
    init {
        River(rapidsConnection).apply {
            validate { it.demandValue("eventName", "hm-NyOrdrelinje-hotsak") }
            validate { it.requireKey("eventId", "opprettet", "fnrBruker", "data") }
        }.register(this)
    }

    private val JsonMessage.eventId get() = uuidValue("eventId")
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

    override suspend fun onPacketAsync(packet: JsonMessage, context: MessageContext) {
        if (packet.saksnummer.isEmpty()) {
            logger.info { "Skipping illegal event HOTSAK saksnummer='': ${packet.eventId}" }
            sikkerlogg.error { "Skippet event med tomt HOTSAK saksnummer: ${packet.toJson()}" }
            return
        }
        if (packet.eventId in skipList) {
            logger.info { "Hopper over event i skipList: ${packet.eventId}" }
            sikkerlogg.error { "Skippet event: ${packet.toJson()}" }
            return
        }
        try {
            logger.info { "Hotsak ordrelinje fra Oebs mottatt med eventId: ${packet.eventId}" }

            // Match ordrelinje to Hotsak-table
            val søknad = søknadForRiverClient.finnSøknadForSak(HotsakSakId(packet.saksnummer))
            if (søknad == null) {
                logger.warn { "Ordrelinje med eventId: ${packet.eventId} og sakId: ${packet.saksnummer} kan ikke matches mot en søknadId" }
                return
            }
            val søknadId = søknad.søknadId

            logger.info { "Fant søknadId: $søknadId fra Hotsak saksnummer: ${packet.saksnummer}" }

            val ordrelinjeData = OrdrelinjeData(
                søknadId = søknadId,
                behovsmeldingType = søknad.behovsmeldingstype,
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

            // fixme -> kunne vi ikke sjekket dette i API-et og oppdatert status der? Hvorfor gjøre dette i en dialog med API-et?
            val ordreSisteDøgn = søknadForRiverClient.ordreSisteDøgn(søknadId = søknadId)
            val result = save(ordrelinjeData)

            if (result == 0) {
                return
            }

            søknadForRiverClient.oppdaterStatus(søknadId, BehovsmeldingStatus.UTSENDING_STARTET)

            context.publish(
                ordrelinjeData.fnrBruker,
                packet.data.asObject<Map<String, Any?>>() + mapOf<String, Any?>(
                    "eventId" to UUID.randomUUID(),
                    "eventName" to "hm-OrdrelinjeMottatt",
                    "opprettet" to packet.opprettet,
                    "søknadId" to søknadId,
                    "behovsmeldingType" to ordrelinjeData.behovsmeldingType,
                )
            )

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
        } catch (e: Exception) {
            throw RuntimeException("Håndtering av event ${packet.eventId} feilet", e)
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

    private val skipList = listOf<UUID>()
}
