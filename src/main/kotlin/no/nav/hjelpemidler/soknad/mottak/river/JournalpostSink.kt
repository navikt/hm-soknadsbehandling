package no.nav.hjelpemidler.soknad.mottak.river

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.hjelpemidler.soknad.mottak.client.SøknadForRiverClient

private val logger = KotlinLogging.logger {}

class JournalpostSink(
    rapidsConnection: RapidsConnection,
    private val søknadForRiverClient: SøknadForRiverClient,
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
            val rowsUpdated = søknadForRiverClient.oppdaterJournalpostId(søknadId, journalpostId)
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
