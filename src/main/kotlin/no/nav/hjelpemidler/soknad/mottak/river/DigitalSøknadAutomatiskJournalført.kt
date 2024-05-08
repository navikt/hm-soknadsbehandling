package no.nav.hjelpemidler.soknad.mottak.river

import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.hjelpemidler.soknad.mottak.client.SøknadForRiverClient
import no.nav.hjelpemidler.soknad.mottak.service.BehovsmeldingType
import no.nav.hjelpemidler.soknad.mottak.service.Status
import no.nav.hjelpemidler.soknad.mottak.service.SøknadUnderBehandlingData
import java.util.UUID

private val logger = KotlinLogging.logger {}
private val sikkerlogg = KotlinLogging.logger("tjenestekall")

internal class DigitalSøknadAutomatiskJournalført(
    rapidsConnection: RapidsConnection,
    private val søknadForRiverClient: SøknadForRiverClient,
) : PacketListenerWithOnError {

    init {
        River(rapidsConnection).apply {
            validate { it.demandValue("eventName", "hm-opprettetOgFerdigstiltJournalpost") }
            validate { it.requireKey("soknadId", "sakId", "joarkRef", "fnrBruker") }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        logger.info(
            """
            Received journalpostref with søknad id: ${packet["soknadId"].asText()} and journalpostref: ${packet["joarkRef"].asText()}
            """.trimIndent()
        )

        val søknadId = packet["soknadId"].asText()
        val søknadIdUid = UUID.fromString(søknadId)
        val fnrBruker = packet["fnrBruker"].asText()

        runBlocking {
            val behovsmeldingType = søknadForRiverClient.behovsmeldingTypeFor(søknadIdUid) ?: BehovsmeldingType.SØKNAD
            val rowsUpdated = oppdaterStatus(søknadIdUid)

            if (rowsUpdated > 0) {
                logger.info(
                    "Status på ${
                        behovsmeldingType.toString().lowercase()
                    } satt til endelig journalført: $søknadId"
                )

                // Melding til Ditt NAV
                context.publish(
                    fnrBruker,
                    SøknadUnderBehandlingData(
                        UUID.fromString(søknadId),
                        fnrBruker,
                        behovsmeldingType,
                    ).toJson("hm-SøknadUnderBehandling")
                )
            } else {
                logger.warn("Status er allerede satt til endelig journalført, søknadId: $søknadId")
            }
        }
    }

    private suspend fun oppdaterStatus(søknadId: UUID) =
        runCatching {
            søknadForRiverClient.oppdaterStatus(søknadId, Status.ENDELIG_JOURNALFØRT)
        }.onFailure {
            logger.error(it) { "Failed to update søknad to endelig journalført, søknadId: $søknadId" }
        }.getOrThrow()
}
