package no.nav.hjelpemidler.soknad.mottak.river

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.hjelpemidler.soknad.mottak.client.SøknadsbehandlingClient

private val log = KotlinLogging.logger {}

class OppgaveSink(
    rapidsConnection: RapidsConnection,
    private val søknadsbehandlingClient: SøknadsbehandlingClient,
) : AsyncPacketListener {
    init {
        River(rapidsConnection).apply {
            precondition {
                it.requireAny(
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
                log.info { "Søknad med søknadId: $søknadId oppdatert med oppgaveId: $oppgaveId" }
            } else {
                log.error {
                    "Kunne ikke oppdatere søknadId: $søknadId med oppgaveId: $oppgaveId. Kontroller at søknadId eksisterer og ikke allerede har registrert en oppgaveId."
                }
            }
        } catch (e: Exception) {
            log.error(e) { "Håndtering av ny oppgaveId: $oppgaveId for søknadId: $søknadId feilet" }
            throw e
        }
    }
}
