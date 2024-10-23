package no.nav.hjelpemidler.soknad.mottak.river

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.hjelpemidler.soknad.mottak.client.SøknadsbehandlingClient

private val logger = KotlinLogging.logger {}

class JournalpostSink(
    rapidsConnection: RapidsConnection,
    private val søknadsbehandlingClient: SøknadsbehandlingClient,
) : AsyncPacketListener {
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

    private val JsonMessage.søknadId get() = uuidValue("soknadId")
    private val JsonMessage.journalpostId get() = this["joarkRef"].textValue()

    override suspend fun onPacketAsync(packet: JsonMessage, context: MessageContext) {
        val søknadId = packet.søknadId
        val journalpostId = packet.journalpostId
        try {
            val rowsUpdated = søknadsbehandlingClient.oppdaterJournalpostId(søknadId, journalpostId)
            if (rowsUpdated > 0) {
                logger.info { "Søknad med søknadId: $søknadId oppdatert med journalpostId: $journalpostId" }
            } else {
                logger.error {
                    "Kunne ikke oppdatere søknadId: $søknadId med journalpostId: $journalpostId. Kontroller at søknadId eksisterer og ikke allerede har registrert en journalpostId."
                }
            }
        } catch (e: Exception) {
            logger.error(e) { "Håndtering av ny journalpostId: $journalpostId for søknadId: $søknadId feilet" }
            throw e
        }
    }
}
