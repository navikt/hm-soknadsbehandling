package no.nav.hjelpemidler.soknad.mottak.river

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.hjelpemidler.behovsmeldingsmodell.TilknyttetSøknad
import no.nav.hjelpemidler.soknad.mottak.client.SøknadsbehandlingClient
import no.nav.hjelpemidler.soknad.mottak.melding.Melding
import java.util.UUID

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

    private val JsonMessage.eventName get() = this["eventName"].textValue()
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

        try {
            if (søknadsbehandlingClient.finnSøknad(søknadId)?.digital == true) {
                log.info { "Sender oppgave for digital søknad opprettet til infotrygd-poller for søknadId: $søknadId, oppgaveId: $oppgaveId" }
                context.publish(
                    søknadId.toString(),
                    DigitalSøknadOppgaveIdMelding(
                        søknadId = søknadId,
                        oppgaveId = oppgaveId,
                        tilbakeført = packet.eventName == "hm-opprettetJournalføringsoppgaveForTilbakeførtSak",
                    )
                )
            }
        } catch (e: Exception) {
            log.error(e) { "Feilet i å sjekke om søknad er digital eller å sende melding om digital oppgaveid" }
        }
    }
}

data class DigitalSøknadOppgaveIdMelding(
    override val søknadId: UUID,
    val oppgaveId: String,
    val tilbakeført: Boolean,
) : TilknyttetSøknad, Melding {
    override val eventId: UUID = UUID.randomUUID()
    override val eventName: String = "hm-digital-søknad-oppgaveId"
}
