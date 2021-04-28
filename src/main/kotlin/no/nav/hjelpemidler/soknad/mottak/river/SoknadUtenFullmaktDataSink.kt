package no.nav.hjelpemidler.soknad.mottak.river

import com.fasterxml.jackson.databind.JsonNode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.hjelpemidler.soknad.mottak.JacksonMapper
import no.nav.hjelpemidler.soknad.mottak.client.SøknadForRiverClient
import no.nav.hjelpemidler.soknad.mottak.metrics.Prometheus
import no.nav.hjelpemidler.soknad.mottak.service.SoknadData
import no.nav.hjelpemidler.soknad.mottak.service.Status
import java.util.UUID

private val logger = KotlinLogging.logger {}
private val sikkerlogg = KotlinLogging.logger("tjenestekall")

internal class SoknadUtenFullmaktDataSink(
    rapidsConnection: RapidsConnection,
    private val søknadForRiverClient: SøknadForRiverClient
) : PacketListenerWithOnError {

    private fun soknadToJson(soknad: JsonNode): String = JacksonMapper.objectMapper.writeValueAsString(soknad)

    init {
        River(rapidsConnection).apply {
            validate { it.demandValue("eventName", "nySoknad") }
            validate { it.demandValue("signatur", "BRUKER_BEKREFTER") }
            validate { it.requireKey("fodselNrBruker", "fodselNrInnsender", "soknad", "eventId", "kommunenavn") }
            validate { it.forbid("soknadId") }
        }.register(this)
    }

    private val JsonMessage.eventId get() = this["eventId"].textValue()
    private val JsonMessage.fnrBruker get() = this["fodselNrBruker"].textValue()
    private val JsonMessage.fnrInnsender get() = this["fodselNrInnsender"].textValue()
    private val JsonMessage.soknadId get() = this["soknad"]["soknad"]["id"].textValue()
    private val JsonMessage.soknad get() = this["soknad"]
    private val JsonMessage.kommunenavn get() = this["kommunenavn"].textValue()
    private val JsonMessage.navnBruker get() = this["soknad"]["soknad"]["bruker"]["fornavn"].textValue() + " " + this["soknad"]["soknad"]["bruker"]["etternavn"].textValue()

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
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
                            navnBruker = packet.navnBruker,
                            fnrInnsender = packet.fnrInnsender,
                            soknad = packet.soknad,
                            soknadId = UUID.fromString(packet.soknadId),
                            status = Status.VENTER_GODKJENNING,
                            kommunenavn = packet.kommunenavn
                        )
                        if (søknadForRiverClient.soknadFinnes(soknadData.soknadId)) {
                            logger.warn { "Søknaden er allerede lagret i databasen: ${packet.soknadId}" }
                            return@launch
                        }

                        logger.info { "Søknad til godkjenning mottatt: ${packet.soknadId}" }
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
        return skipList.any { it == eventId }
    }

    private suspend fun save(soknadData: SoknadData) =
        kotlin.runCatching {
            søknadForRiverClient.save(soknadData)
        }.onSuccess {
            logger.info("Søknad klar til godkjenning saved: ${soknadData.soknadId}")
        }.onFailure {
            logger.error(it) { "Failed to save søknad klar til godkjenning: ${soknadData.soknadId}" }
        }.getOrThrow()

    private fun CoroutineScope.forward(søknadData: SoknadData, context: MessageContext) {
        launch(Dispatchers.IO + SupervisorJob()) {
            context.publish(søknadData.fnrBruker, søknadData.toVenterPaaGodkjenningJson())
            Prometheus.soknadTilGodkjenningCounter.inc()
        }.invokeOnCompletion {
            when (it) {
                null -> {
                    logger.info("Søknad klar til godkjenning: ${søknadData.soknadId}")
                    sikkerlogg.info("Søknad klar til godkjenning med søknadsId: ${søknadData.soknadId}, fnr: ${søknadData.fnrBruker})")
                }
                is CancellationException -> logger.warn("Cancelled: ${it.message}")
                else -> {
                    logger.error("Failed: ${it.message}. Soknad: ${søknadData.soknadId}")
                }
            }
        }
    }
}
