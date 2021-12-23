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
            validate { it.demandValue("eventName", "hm-OppgaveOpprettet") }
            validate { it.requireKey("soknadId", "oppgaveId") }
        }.register(this)
    }

    private val JsonMessage.soknadId get() = this["soknadId"].textValue()
    private val JsonMessage.oppgaveId get() = this["oppgaveId"].textValue()

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        runBlocking {
            try {
                val rowsUpdated = update(UUID.fromString(packet.soknadId), packet.oppgaveId)
                if (rowsUpdated > 0) {
                    logger.info("Søknad ${packet.soknadId} oppdatert med oppgaveId ${packet.oppgaveId}")
                } else {
                    logger.error {
                        "Kunne ikke oppdatere søknad ${packet.soknadId} med oppgaveId ${packet.oppgaveId}. " +
                            "Kontroller at soknadId eksisterer og ikke allerede har registrert en oppgaveId."
                    }
                }
            } catch (e: Exception) {
                throw RuntimeException(
                    "Håndtering av ny oppgaveId (${packet.oppgaveId}) for søknad ${packet.soknadId} feilet",
                    e
                )
            }
        }
    }

    private suspend fun update(soknadId: UUID, oppgaveId: String) =
        kotlin.runCatching {
            søknadForRiverClient.oppdaterOppgaveId(soknadId, oppgaveId)
        }.onFailure {
            logger.error(it) { "Kunne ikke oppdatere søknad $soknadId med oppgaveId $oppgaveId" }
        }.getOrThrow()
}
