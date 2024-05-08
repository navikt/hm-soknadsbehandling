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

internal class OppgaveSink(rapidsConnection: RapidsConnection, private val søknadForRiverClient: SøknadForRiverClient) :
    PacketListenerWithOnError {

    init {
        River(rapidsConnection).apply {
            validate {
                it.demandAny(
                    "eventName",
                    listOf("hm-OppgaveOpprettet", "hm-opprettetJournalføringsoppgaveForTilbakeførtSak")
                )
            }
            validate { it.requireKey("soknadId", "oppgaveId") }
        }.register(this)
    }

    private val JsonMessage.søknadId get() = this["soknadId"].textValue()
    private val JsonMessage.oppgaveId get() = this["oppgaveId"].textValue()

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        runBlocking {
            try {
                val rowsUpdated = update(UUID.fromString(packet.søknadId), packet.oppgaveId)
                if (rowsUpdated > 0) {
                    logger.info("Søknad med søknadId: ${packet.søknadId} oppdatert med oppgaveId: ${packet.oppgaveId}")
                } else {
                    logger.error {
                        "Kunne ikke oppdatere søknadId: ${packet.søknadId} med oppgaveId: ${packet.oppgaveId}. Kontroller at søknadId eksisterer og ikke allerede har registrert en oppgaveId."
                    }
                }
            } catch (e: Exception) {
                throw RuntimeException(
                    "Håndtering av ny oppgaveId: ${packet.oppgaveId} for søknadId: ${packet.søknadId} feilet",
                    e
                )
            }
        }
    }

    private suspend fun update(søknadId: UUID, oppgaveId: String) =
        runCatching {
            søknadForRiverClient.oppdaterOppgaveId(søknadId, oppgaveId)
        }.onFailure {
            logger.error(it) { "Kunne ikke oppdatere søknadId: $søknadId med oppgaveId: $oppgaveId" }
        }.getOrThrow()
}
