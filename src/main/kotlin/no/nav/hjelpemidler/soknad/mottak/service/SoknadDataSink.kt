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
import no.nav.hjelpemidler.soknad.mottak.db.SoknadStore
import no.nav.hjelpemidler.soknad.mottak.metrics.Prometheus
import java.util.UUID

private val logger = KotlinLogging.logger {}
private val sikkerlogg = KotlinLogging.logger("tjenestekall")

internal class SoknadDataSink(rapidsConnection: RapidsConnection, private val store: SoknadStore) :
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
            validate { it.forbid("soknadId", "eventName") }
            validate { it.requireKey("fodselNrBruker", "fodselNrInnsender", "soknad", "eventId") }
        }.register(this)
    }
    private val JsonMessage.eventId get() = this["eventId"].textValue()
    private val JsonMessage.fnrBruker get() = this["fodselNrBruker"].textValue()
    private val JsonMessage.fnrInnsender get() = this["fodselNrInnsender"].textValue()
    private val JsonMessage.soknadId get() = this["soknad"]["soknad"]["id"].textValue()
    private val JsonMessage.soknad get() = this["soknad"]
    private val JsonMessage.navnBruker get() = this["soknad"]["soknad"]["bruker"]["etternavn"].textValue() + " " + this["soknad"]["soknad"]["bruker"]["fornavn"].textValue()
    private val JsonMessage.signatur get() =
        if (this["soknad"]["soknad"]["bruker"].has("signatur")) {
            this["soknad"]["soknad"]["bruker"]["signatur"].textValue()
        } else "FULLMAKT"

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
                            status = lagStatus(packet.signatur)
                        )
                        logger.info { "Søknad mottatt: ${packet.soknadId}" }
                        save(soknadData)

                        forward(soknadData, context)
                    } catch (e: Exception) {
                        throw RuntimeException("Håndtering av event ${packet.eventId} feilet", e)
                    }
                }
            }
        }
    }

    private fun lagStatus(signatur: String): Status {
        return when (signatur) {
            "BRUKER_BEKREFTER" -> Status.VENTER_GODKJENNING
            "FULLMAKT" -> Status.GODKJENT_MED_FULLMAKT
            else -> { throw RuntimeException("Ukjent signaturtype i søknad") }
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
            Prometheus.soknadSendtCounter.inc()
        }.invokeOnCompletion {
            when (it) {
                null -> {
                    logger.info("Søknad sent: ${søknadData.soknadId}")
                    sikkerlogg.info("Søknad sent med søknadsId: ${søknadData.soknadId}, fnr: ${søknadData.fnrBruker})")
                }
                is CancellationException -> logger.warn("Cancelled: ${it.message}")
                else -> {
                    logger.error("Failed: ${it.message}. Soknad: ${søknadData.soknadId}")
                }
            }
        }
    }
}
