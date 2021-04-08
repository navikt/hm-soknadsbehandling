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

internal class PapirSøknadEndeligJournalført(rapidsConnection: RapidsConnection, private val store: SøknadStore) :
    River.PacketListener {
    init {
        River(rapidsConnection).apply {
            validate { it.demandValue("eventName", "PapirSoeknadEndeligJournalfoert") }
            validate {
                it.requireKey(
                    "fodselNrBruker",
                    "hendelse",
                    "hendelse.journalingEvent",
                    "hendelse.journalingEvent.journalpostId",
                    "eventId"
                )
            }
        }.register(this)
    }

    private val JsonMessage.eventId get() = this["eventId"].textValue()
    private val JsonMessage.fnrBruker get() = this["fodselNrBruker"].textValue()
    private val JsonMessage.journalpostId get() = this["hendelse"]["journalingEvent"]["journalpostId"].asInt()

    override fun onPacket(packet: JsonMessage, context: RapidsConnection.MessageContext) {
        runBlocking {
            withContext(Dispatchers.IO) {
                launch {

                    if (skipEvent(UUID.fromString(packet.eventId))) {
                        logger.info { "Hopper over event i skip-list: ${packet.eventId}" }
                        return@launch
                    }

                    val søknadId = UUID.randomUUID()

                    try {
                        val soknadData = PapirSøknadData(
                            fnrBruker = packet.fnrBruker,
                            soknadId = søknadId,
                            status = Status.ENDELIG_JOURNALFØRT,
                            journalpostid = packet.journalpostId
                        )

                        if (store.soknadFinnes(soknadData.soknadId)) {
                            logger.warn { "En søknad med denne id-en er allerede lagret i databasen: $søknadId" }
                            return@launch
                        }

                        save(soknadData)
                        logger.info { "Papirsøknad mottatt og lagret: $søknadId" }

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
            if (it > 0) {
                logger.info("Papirsøknad klar til godkjenning saved: ${soknadData.soknadId} it=$it")
            } else {
                logger.error("Lagring av papirsøknad feilet. Ingen rader påvirket under lagring.")
            }
        }.onFailure {
            logger.error(it) { "Failed to save papirsøknad klar til godkjenning: ${soknadData.soknadId}" }
        }.getOrThrow()

    private fun CoroutineScope.forward(søknadData: PapirSøknadData, context: RapidsConnection.MessageContext) {
        launch(Dispatchers.IO + SupervisorJob()) {
            context.send(søknadData.fnrBruker, søknadData.toJson("hm-papirsøknadMottatt"))
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
