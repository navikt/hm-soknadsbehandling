package no.nav.hjelpemidler.soknad.mottak.service

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

internal class GodkjennSoknad(rapidsConnection: RapidsConnection, private val store: SøknadStore) :
    River.PacketListener {

    init {
        River(rapidsConnection).apply {
            validate { it.requireValue("eventName", "godkjentAvBruker") }
            validate { it.requireKey("soknadId") }
        }.register(this)
    }

    private val JsonMessage.soknadId get() = this["soknadId"].textValue()

    override fun onPacket(packet: JsonMessage, context: RapidsConnection.MessageContext) {
        runBlocking {
            withContext(Dispatchers.IO) {
                launch {
                    try {
                        logger.info { "Bruker har godkjent søknad: ${packet.soknadId}" }
                        val rowsUpdated = update(UUID.fromString(packet.soknadId), Status.GODKJENT)
                        if (rowsUpdated> 0) {
                            val soknad = hentSoknadData(UUID.fromString(packet.soknadId))
                            forward(soknad, context)
                        } else {
                            logger.info { "Søknad som godkjennes er allerede godkjent, søknadId: ${packet.soknadId}" }
                        }
                    } catch (e: Exception) {
                        throw RuntimeException("Håndtering av brukergodkjenning for søknad ${packet.soknadId} feilet", e)
                    }
                }
            }
        }
    }

    private fun update(soknadId: UUID, status: Status) =
        kotlin.runCatching {
            store.oppdaterStatus(soknadId, status)
        }.onSuccess {
            logger.info("Søknad $soknadId oppdatert med status $status")
        }.onFailure {
            logger.error(it) { "Failed to update søknad $soknadId med status $status" }
        }.getOrThrow()

    private fun hentSoknadData(soknadId: UUID): SoknadData =
        kotlin.runCatching {
            store.hentSoknadData(soknadId)!!
        }.onFailure {
            logger.error(it) { "Failed to retrieve søknad $soknadId" }
        }.getOrThrow()

    private fun CoroutineScope.forward(soknadData: SoknadData, context: RapidsConnection.MessageContext) {
        val fnrBruker = soknadData.fnrBruker
        val soknadId = soknadData.soknadId.toString()

        launch(Dispatchers.IO + SupervisorJob()) {
            val soknadGodkjentMessage = soknadData.toJson("SøknadGodkjentAvBruker")
            context.send(fnrBruker, soknadGodkjentMessage)
            Prometheus.soknadGodkjentAvBrukerCounter.inc()
        }.invokeOnCompletion {
            when (it) {
                null -> {
                    logger.info("Søknad er godkjent av bruker: $soknadId")
                    sikkerlogg.info("Søknad er godkjent med søknadsId: $soknadId, fnr: $fnrBruker)")
                }
                is CancellationException -> logger.warn("Cancelled: ${it.message}")
                else -> {
                    logger.error("Failed: ${it.message}. Soknad: $soknadId")
                }
            }
        }
    }
}
