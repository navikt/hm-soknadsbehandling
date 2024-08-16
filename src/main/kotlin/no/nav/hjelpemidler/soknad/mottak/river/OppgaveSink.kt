package no.nav.hjelpemidler.soknad.mottak.river

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.hjelpemidler.soknad.mottak.client.SøknadsbehandlingClient

private val logger = KotlinLogging.logger {}

class OppgaveSink(
    rapidsConnection: RapidsConnection,
    private val søknadsbehandlingClient: SøknadsbehandlingClient,
) : AsyncPacketListener {
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

    private val JsonMessage.søknadId get() = uuidValue("soknadId")
    private val JsonMessage.oppgaveId get() = this["oppgaveId"].textValue()

    override suspend fun onPacketAsync(packet: JsonMessage, context: MessageContext) {
        val søknadId = packet.søknadId
        val oppgaveId = packet.oppgaveId
        try {
            val rowsUpdated = søknadsbehandlingClient.oppdaterOppgaveId(søknadId, oppgaveId)
            if (rowsUpdated > 0) {
                logger.info { "Søknad med søknadId: $søknadId oppdatert med oppgaveId: $oppgaveId" }
            } else {
                logger.error {
                    "Kunne ikke oppdatere søknadId: $søknadId med oppgaveId: $oppgaveId. Kontroller at søknadId eksisterer og ikke allerede har registrert en oppgaveId."
                }
            }
        } catch (e: Exception) {
            logger.error(e) { "Håndtering av ny oppgaveId: $oppgaveId for søknadId: $søknadId feilet" }
            throw e
        }
    }
}
