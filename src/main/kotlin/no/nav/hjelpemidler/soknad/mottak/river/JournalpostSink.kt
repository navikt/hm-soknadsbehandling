package no.nav.hjelpemidler.soknad.mottak.river

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.hjelpemidler.soknad.mottak.client.SøknadForRiverClient
import java.util.UUID

private val logger = KotlinLogging.logger {}
private val sikkerlogg = KotlinLogging.logger("tjenestekall")

internal class JournalpostSink(rapidsConnection: RapidsConnection, private val søknadForRiverClient: SøknadForRiverClient) : PacketListenerAbstract {

    init {
        River(rapidsConnection).apply {
            validate { it.demandValue("eventName", "hm-SøknadArkivert") }
            validate { it.requireKey("soknadId", "joarkRef") }
        }.register(this)
    }

    private val JsonMessage.soknadId get() = this["soknadId"].textValue()
    private val JsonMessage.journalpostId get() = this["joarkRef"].textValue()

    override fun onPacket(packet: JsonMessage, context: RapidsConnection.MessageContext) {
        runBlocking {
            withContext(Dispatchers.IO) {
                launch {
                    try {
                        val rowsUpdated = update(UUID.fromString(packet.soknadId), packet.journalpostId)
                        if (rowsUpdated > 0) {
                            logger.info("Søknad ${packet.soknadId} oppdatert med journalpostId ${packet.journalpostId}")
                        } else {
                            logger.error {
                                "Kunne ikke oppdatere søknad ${packet.soknadId} med journlapostId ${packet.journalpostId}. " +
                                    "Kontroller at soknadId eksisterer og ikke allerede har registrert en journalpostId."
                            }
                        }
                    } catch (e: Exception) {
                        throw RuntimeException("Håndtering av ny journalpostId (${packet.journalpostId}) for søknad ${packet.soknadId} feilet", e)
                    }
                }
            }
        }
    }

    private suspend fun update(soknadId: UUID, journalpostId: String) =
        kotlin.runCatching {
            søknadForRiverClient.oppdaterJournalpostId(soknadId, journalpostId)
        }.onFailure {
            logger.error(it) { "Kunne ikke oppdatere søknad $soknadId med journlapostId $journalpostId" }
        }.getOrThrow()
}
