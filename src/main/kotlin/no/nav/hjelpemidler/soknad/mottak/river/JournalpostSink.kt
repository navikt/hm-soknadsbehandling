package no.nav.hjelpemidler.soknad.mottak.river

import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.hjelpemidler.soknad.mottak.client.SøknadForRiverClient
import java.util.UUID

private val logger = KotlinLogging.logger {}
private val sikkerlogg = KotlinLogging.logger("tjenestekall")

internal class JournalpostSink(
    rapidsConnection: RapidsConnection,
    private val søknadForRiverClient: SøknadForRiverClient,
) : PacketListenerWithOnError {

    init {
        River(rapidsConnection).apply {
            validate {
                it.demandAny(
                    "eventName",
                    listOf(
                        "hm-SøknadArkivert",
                        "hm-opprettetOgFerdigstiltJournalpost",
                        "hm-opprettetMottattJournalpost"
                    )
                )
            }
            validate { it.requireKey("soknadId", "joarkRef") }
        }.register(this)
    }

    private val JsonMessage.søknadId get() = this["soknadId"].textValue()
    private val JsonMessage.journalpostId get() = this["joarkRef"].textValue()

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        runBlocking {
            try {
                val rowsUpdated = update(UUID.fromString(packet.søknadId), packet.journalpostId)
                if (rowsUpdated > 0) {
                    logger.info("Søknad ${packet.søknadId} oppdatert med journalpostId: ${packet.journalpostId}")
                } else {
                    logger.error {
                        "Kunne ikke oppdatere søknadId: ${packet.søknadId} med journalpostId: ${packet.journalpostId}. Kontroller at søknadId eksisterer og ikke allerede har registrert en journalpostId."
                    }
                }
            } catch (e: Exception) {
                throw RuntimeException(
                    "Håndtering av ny journalpostId: ${packet.journalpostId} for søknadId: ${packet.søknadId} feilet",
                    e
                )
            }
        }
    }

    private suspend fun update(søknadId: UUID, journalpostId: String) =
        runCatching {
            søknadForRiverClient.oppdaterJournalpostId(søknadId, journalpostId)
        }.onFailure {
            logger.error(it) { "Kunne ikke oppdatere søknadId: $søknadId med journalpostId: $journalpostId" }
        }.getOrThrow()
}
