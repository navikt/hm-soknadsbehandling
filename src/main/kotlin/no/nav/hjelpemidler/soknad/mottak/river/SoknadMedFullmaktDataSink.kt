package no.nav.hjelpemidler.soknad.mottak.river

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageProblems
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.hjelpemidler.soknad.mottak.client.SøknadForRiverClient
import no.nav.hjelpemidler.soknad.mottak.metrics.Prometheus
import no.nav.hjelpemidler.soknad.mottak.service.SoknadData
import no.nav.hjelpemidler.soknad.mottak.service.Status
import java.util.UUID

private val logger = KotlinLogging.logger {}
private val sikkerlogg = KotlinLogging.logger("tjenestekall")

internal class SoknadMedFullmaktDataSink(rapidsConnection: RapidsConnection, private val søknadForRiverClient: SøknadForRiverClient) :
    River.PacketListener {

    init {
        River(rapidsConnection).apply {
            validate { it.demandValue("eventName", "nySoknad") }
            validate { it.demandValue("signatur", "FULLMAKT") }
            validate { it.requireKey("fodselNrBruker", "fodselNrInnsender", "soknad", "eventId", "kommunenavn") }
        }.register(this)
    }

    private val JsonMessage.eventId get() = this["eventId"].textValue()
    private val JsonMessage.fnrBruker get() = this["fodselNrBruker"].textValue()
    private val JsonMessage.fnrInnsender get() = this["fodselNrInnsender"].textValue()
    private val JsonMessage.soknadId get() = this["soknad"]["soknad"]["id"].textValue()
    private val JsonMessage.soknad get() = this["soknad"]
    private val JsonMessage.kommunenavn get() = this["kommunenavn"].textValue()
    private val JsonMessage.navnBruker get() = this["soknad"]["soknad"]["bruker"]["fornavn"].textValue() + " " + this["soknad"]["soknad"]["bruker"]["etternavn"].textValue()

    override fun onError(problems: MessageProblems, context: RapidsConnection.MessageContext) {
        sikkerlogg.info("River required keys had problems in parsing message from rapid: ${problems.toExtendedReport()}")
        throw Exception("River required keys had problems in parsing message from rapid, see Kibana index tjenestekall-* (sikkerlogg) for details")
    }

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
                            navnBruker = packet.navnBruker,
                            fnrInnsender = packet.fnrInnsender,
                            soknad = packet.soknad,
                            soknadId = UUID.fromString(packet.soknadId),
                            status = Status.GODKJENT_MED_FULLMAKT,
                            kommunenavn = packet.kommunenavn,
                        )
                        if (søknadForRiverClient.soknadFinnes(soknadData.soknadId)) {
                            logger.warn { "Søknaden er allerede lagret i databasen: ${packet.soknadId}" }
                            return@launch
                        }

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

    private suspend fun save(soknadData: SoknadData) =
        kotlin.runCatching {
            søknadForRiverClient.save(soknadData)
        }.onSuccess {
            logger.info("Søknad saved: ${soknadData.soknadId}")
        }.onFailure {
            logger.error(it) { "Failed to save søknad: ${soknadData.soknadId}" }
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
