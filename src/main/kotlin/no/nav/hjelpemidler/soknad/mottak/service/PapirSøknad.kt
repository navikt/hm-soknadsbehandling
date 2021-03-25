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

internal class PapirSøknad(rapidsConnection: RapidsConnection, private val store: SøknadStore) :
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
            validate { it.demandValue("eventName", "PapirSoeknadEndeligJournalfoert") }
            validate { it.requireKey("hendelse.avsenderMottaker.id", "fodselNrBruker") }
        }.register(this)
    }

    private val JsonMessage.eventId get() = this["eventId"].textValue()
    private val JsonMessage.fnrBruker get() = this["fodselNrBruker"].textValue()

    override fun onPacket(packet: JsonMessage, context: RapidsConnection.MessageContext) {
        runBlocking {
            withContext(Dispatchers.IO) {
                launch {
                    // new id for this papirsøknad
                    val søknadId = UUID.randomUUID()

                    try {
                        val soknadData = PapirSøknadData(
                            fnrBruker = packet.fnrBruker,
                            soknadId = søknadId,
                            status = Status.ENDELIG_JOURNALFØRT,
                        )

                        if (store.soknadFinnes(soknadData.soknadId)) {
                            logger.warn { "En søknad med denne id-en er allerede lagret i databasen: $søknadId" }
                            return@launch
                        }

                        logger.info { "Papirsøknad mottatt og lagret: $søknadId" }
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

    private fun save(soknadData: PapirSøknadData) =
        kotlin.runCatching {
            store.savePapir(soknadData)
        }.onSuccess {
            logger.info("Papirsøknad klar til godkjenning saved: ${soknadData.soknadId}")
        }.onFailure {
            logger.error(it) { "Failed to save papirsøknad klar til godkjenning: ${soknadData.soknadId}" }
        }.getOrThrow()

    private fun CoroutineScope.forward(søknadData: PapirSøknadData, context: RapidsConnection.MessageContext) {
        launch(Dispatchers.IO + SupervisorJob()) {
            context.send(søknadData.fnrBruker, søknadData.toJson("papirsøknadMottatt"))
            Prometheus.papirSøknadMottatt.inc()
        }.invokeOnCompletion {
            when (it) {
                null -> {
                    logger.info("Papirsøknad registrert: ${søknadData.soknadId}")
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
