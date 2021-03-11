package no.nav.hjelpemidler.soknad.mottak.service

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.hjelpemidler.soknad.mottak.db.SøknadStore
import no.nav.hjelpemidler.soknad.mottak.metrics.Prometheus
import java.util.UUID

private val logger = KotlinLogging.logger {}
private val sikkerlogg = KotlinLogging.logger("tjenestekall")

internal class SoknadMedFullmaktDataSink(rapidsConnection: RapidsConnection, private val store: SøknadStore) :
    River.PacketListener {

    companion object {
        private val objectMapper = jacksonObjectMapper()
            .registerModule(JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
    }

    private fun soknadToJson(soknad: JsonNode): String = objectMapper.writeValueAsString(soknad)

    init {
        River(rapidsConnection).apply {
            validate { it.requireValue("eventName", "nySoknad") }
            validate { it.requireValue("signatur", "FULLMAKT") }
            validate { it.forbid("soknadId") }
            validate { it.interestedIn("fodselNrBruker", "fodselNrInnsender", "soknad", "eventId", "kommunenavn") }
        }.register(this)
    }

    private val JsonMessage.eventId get() = this["eventId"].textValue()
    private val JsonMessage.fnrBruker get() = this["fodselNrBruker"].textValue()
    private val JsonMessage.fnrInnsender get() = this["fodselNrInnsender"].textValue()
    private val JsonMessage.soknadId get() = this["soknad"]["soknad"]["id"].textValue()
    private val JsonMessage.soknad get() = this["soknad"]
    private val JsonMessage.navnBruker get() = this["soknad"]["soknad"]["bruker"]["etternavn"].textValue() + " " + this["soknad"]["soknad"]["bruker"]["fornavn"].textValue()
    private val JsonMessage.kommunenavn get() = this["kommunenavn"]?.textValue()

    override fun onPacket(packet: JsonMessage, context: RapidsConnection.MessageContext) {
        runBlocking {
            withContext(Dispatchers.IO) {
                launch {
                    if (skipEvent(UUID.fromString(packet.eventId))) {
                        logger.info { "Hopper over event i skip-list: ${packet.eventId}" }
                        return@launch
                    }
                    try {
                        val soknadData = SoknadData(
                            fnrBruker = packet.fnrBruker,
                            fnrInnsender = packet.fnrInnsender,
                            navnBruker = packet.navnBruker,
                            soknadJson = soknadToJson(packet.soknad),
                            soknad = packet.soknad,
                            soknadId = UUID.fromString(packet.soknadId),
                            status = Status.GODKJENT_MED_FULLMAKT,
                            kommunenavn = packet?.kommunenavn
                        )
                        logger.info { "Søknad med fullmakt mottatt: ${packet.soknadId}" }
                        save(soknadData)

                        forward(soknadData, context)
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

    private fun save(soknadData: SoknadData) =
        kotlin.runCatching {
            store.save(soknadData)
        }.onSuccess {
            logger.info("Søknad saved: ${soknadData.soknadId}")
        }.onFailure {
            logger.error(it) { "Failed to save søknad: ${soknadData.soknadId}" }
        }.getOrThrow()

    private fun CoroutineScope.forward(søknadData: SoknadData, context: RapidsConnection.MessageContext) {
        launch(Dispatchers.IO + SupervisorJob()) {
            context.send(søknadData.fnrBruker, søknadData.toJson())
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
