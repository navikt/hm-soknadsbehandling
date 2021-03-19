package no.nav.hjelpemidler.soknad.mottak.service

import kotlinx.coroutines.*
import mu.KotlinLogging
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.hjelpemidler.soknad.mottak.db.OrdreStore
import no.nav.hjelpemidler.soknad.mottak.metrics.Prometheus
import java.util.*

private val logger = KotlinLogging.logger {}
private val sikkerlogg = KotlinLogging.logger("tjenestekall")

internal class NyOrdrelinje(rapidsConnection: RapidsConnection, private val store: OrdreStore) :
    River.PacketListener {

    init {
        River(rapidsConnection).apply {
            validate { it.demandValue("eventName", "hm-nyOrdrelinje") }
            validate { it.requireKey("eventId", "opprettet") }
            validate { it.requireKey("fnrBruker, data") }
        }.register(this)
    }

    private val JsonMessage.eventId get() = this["eventId"].textValue()
    private val JsonMessage.opprettet get() = this["opprettet"].textValue()
    private val JsonMessage.fnrBruker get() = this["fnrBruker"].textValue()
    private val JsonMessage.serviceforespoersel get() = this["serviceforespoersel"].textValue()
    private val JsonMessage.ordrenr get() = this["ordrenr"].intValue()
    private val JsonMessage.ordrelinje get() = this["ordrelinje"].textValue()
    private val JsonMessage.vedtaksdato get() = this["vedtaksdato"].textValue()
    private val JsonMessage.artikkelnr get() = this["artikkelnr"].textValue()
    private val JsonMessage.antall get() = this["antall"].intValue()
    private val JsonMessage.data get() = this["data"]

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
                        val soknadId = fetchSoknadsId()

                        val ordrelinjeData = OrdrelinjeData(
                            soknadId = soknadId,
                            fnrBruker = packet.fnrBruker,
                            serviceforespoersel = packet.serviceforespoersel,
                            ordrenr = packet.ordrenr,
                            ordrelinje = packet.ordrelinje,
                            vedtaksdato = packet.vedtaksdato,
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
            store.save(ordrelinje)
        }.onSuccess {
            logger.info("Ordrelinje lagret for SF ${ordrelinje.serviceforespoersel} og ordrenr/ordrelinje ${ordrelinje.ordrenr}/${ordrelinje.ordrelinje}")
        }.onFailure {
            logger.error(it) { "Ordrelinje lagret for SF ${ordrelinje.serviceforespoersel} og ordrenr/ordrelinje ${ordrelinje.ordrenr}/${ordrelinje.ordrelinje}" }
        }.getOrThrow()

    private fun CoroutineScope.forward(søknadData: SoknadData, context: RapidsConnection.MessageContext) {
        launch(Dispatchers.IO + SupervisorJob()) {
            context.send(søknadData.fnrBruker, søknadData.toJson("Søknad"))
            Prometheus.soknadMedFullmaktCounter.inc()
        }.invokeOnCompletion {
            when (it) {
                null -> {
                    logger.info("Søknad sent: ${søknadData.soknadId}")
                    sikkerlogg.info("Søknad sendt med søknadsId: ${søknadData.soknadId}, fnr: ${søknadData.fnrBruker})")
                }
                is CancellationException -> logger.warn("Cancelled: ${it.message}")
                else -> {
                    logger.error("Failed: ${it.message}. Soknad: ${søknadData.soknadId}")
                }
            }
        }
    }
}
