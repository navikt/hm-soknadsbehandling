package no.nav.hjelpemidler.soknad.mottak.service

import kotlinx.coroutines.*
import mu.KotlinLogging
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helse.rapids_rivers.asLocalDate
import no.nav.hjelpemidler.soknad.mottak.db.InfotrygdStore
import no.nav.hjelpemidler.soknad.mottak.db.OrdreStore
import no.nav.hjelpemidler.soknad.mottak.metrics.Prometheus
import java.util.*

private val logger = KotlinLogging.logger {}
private val sikkerlogg = KotlinLogging.logger("tjenestekall")

internal class NyOrdrelinje(rapidsConnection: RapidsConnection, private val ordreStore: OrdreStore, private val infotrygdStore: InfotrygdStore) :
        River.PacketListener {

    init {
        River(rapidsConnection).apply {
            validate { it.demandValue("eventName", "hm-NyOrdrelinje") }
            validate { it.requireKey("eventId", "opprettet") }
            validate { it.requireKey("fnrBruker, data") }
        }.register(this)
    }

    private val JsonMessage.eventId get() = this["eventId"].textValue()
    private val JsonMessage.opprettet get() = this["opprettet"].textValue()
    private val JsonMessage.fnrBruker get() = this["fnrBruker"].textValue()
    private val JsonMessage.serviceforespoersel get() = this["serviceforespoersel"].textValue()
    private val JsonMessage.ordrenr get() = this["ordrenr"].intValue()
    private val JsonMessage.ordrelinje get() = this["ordrelinje"].intValue()
    private val JsonMessage.delordrelinje get() = this["delordrelinje"].intValue()
    private val JsonMessage.artikkelnr get() = this["artikkelnr"].textValue()
    private val JsonMessage.antall get() = this["antall"].intValue()
    private val JsonMessage.data get() = this["data"]

    // Kun brukt til Infotrygd-matching for å finne soknadId
    private val JsonMessage.saksblokkOgSaksnummer get() = this["saksblokkOgSaksnummer"].textValue()
    private val JsonMessage.vedtaksdato get() = this["vedtaksdato"].asLocalDate()

    override fun onPacket(packet: JsonMessage, context: RapidsConnection.MessageContext) {
        runBlocking {
            withContext(Dispatchers.IO) {
                launch {
                    if (skipEvent(UUID.fromString(packet.eventId))) {
                        logger.info { "Hopper over event i skip-list: ${packet.eventId}" }
                        return@launch
                    }
                    try {
                        logger.info { "Ordrelinje fra Oebs mottatt med eventId: ${packet.eventId}" }

                        // Match ordrelinje to Infotrygd-table
                        val soknadId = infotrygdStore.hentSoknadIdFraResultat(packet.fnrBruker, packet.saksblokkOgSaksnummer, packet.vedtaksdato)

                        val ordrelinjeData = OrdrelinjeData(
                                soknadId = soknadId,
                                fnrBruker = packet.fnrBruker,
                                serviceforespoersel = packet.serviceforespoersel,
                                ordrenr = packet.ordrenr,
                                ordrelinje = packet.ordrelinje,
                                delordrelinje = packet.delordrelinje,
                                artikkelnr = packet.artikkelnr,
                                antall = packet.antall,
                                data = packet.data,
                        )

                        save(ordrelinjeData)

                        forward(ordrelinjeData, context)
                    } catch (e: Exception) {
                        throw RuntimeException("Håndtering av event ${packet.eventId} feilet", e)
                    }
                }
            }
        }
    }

    private fun skipEvent(eventId: UUID): Boolean {
        val skipList = mutableListOf<UUID>()
        skipList.add(UUID.fromString("01fc4654-bba8-43b3-807b-8487ab21cea3"))
        return skipList.any { it == eventId }
    }

    private fun save(ordrelinje: OrdrelinjeData) =
            kotlin.runCatching {
                ordreStore.save(ordrelinje)
            }.onSuccess {
                if (it == 0) {
                    logger.warn("Duplikat av ordrelinje for SF ${ordrelinje.serviceforespoersel}, ordrenr ${ordrelinje.ordrenr} og ordrelinje/delordrelinje ${ordrelinje.ordrelinje}/${ordrelinje.delordrelinje} har ikkje blitt lagra")
                } else {
                    logger.info("Lagra ordrelinje for SF ${ordrelinje.serviceforespoersel}, ordrenr ${ordrelinje.ordrenr} og ordrelinje/delordrelinje ${ordrelinje.ordrelinje}/${ordrelinje.delordrelinje}")
                    Prometheus.ordrelinjeLagretCounter.inc()
                }
            }.onFailure {
                logger.error(it) { "Feil under lagring av ordrelinje for SF ${ordrelinje.serviceforespoersel}, ordrenr ${ordrelinje.ordrenr} og ordrelinje/delordrelinje ${ordrelinje.ordrelinje}/${ordrelinje.delordrelinje}" }
            }.getOrThrow()

    private fun CoroutineScope.forward(ordrelinjeData: OrdrelinjeData, context: RapidsConnection.MessageContext) {
        launch(Dispatchers.IO + SupervisorJob()) {
            context.send(ordrelinjeData.fnrBruker, ordrelinjeData.toJson("hm-OrdrelinjeLagret"))
            Prometheus.ordrelinjeLagretOgSendtTilRapidCounter.inc()
        }.invokeOnCompletion {
            when (it) {
                null -> {
                    logger.info("Ordrelinje sendt: ${ordrelinjeData.soknadId}")
                    sikkerlogg.info("Ordrelinje på bruker: ${ordrelinjeData.soknadId}, fnr: ${ordrelinjeData.fnrBruker})")
                }
                is CancellationException -> logger.warn("Cancelled: ${it.message}")
                else -> {
                    logger.error("Failed: ${it.message}")
                }
            }
        }
    }
}
