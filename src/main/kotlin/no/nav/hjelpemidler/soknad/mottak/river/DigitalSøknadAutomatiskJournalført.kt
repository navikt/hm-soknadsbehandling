package no.nav.hjelpemidler.soknad.mottak.river

import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.hjelpemidler.soknad.mottak.client.SøknadForRiverClient
import no.nav.hjelpemidler.soknad.mottak.service.Status
import no.nav.hjelpemidler.soknad.mottak.service.SøknadUnderBehandlingData
import java.util.UUID

private val logger = KotlinLogging.logger {}
private val sikkerlogg = KotlinLogging.logger("tjenestekall")

internal class DigitalSøknadAutomatiskJournalført(
    rapidsConnection: RapidsConnection,
    private val søknadForRiverClient: SøknadForRiverClient
) : PacketListenerWithOnError {

    init {
        River(rapidsConnection).apply {
            validate { it.demandValue("eventName", "hm-opprettetOgFerdigstiltJournalpost") }
            validate { it.requireKey("soknadId", "sakId", "joarkRef", "fnrBruker") }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        logger.info(
            """Received journalpostref with søknad id: ${packet["soknadId"].asText()} 
                and journalpostref: ${packet["joarkRef"].asText()}
                     """.trimMargin()
        )

        val søknadId = packet["soknadId"].asText()
        val fnrBruker = packet["fnrBruker"].asText()

        runBlocking {
            oppdaterStatus(UUID.fromString(søknadId))
            // Melding til Ditt NAV
            context.publish(
                fnrBruker,
                SøknadUnderBehandlingData(
                    UUID.fromString(søknadId),
                    fnrBruker
                ).toJson("hm-SøknadUnderBehandling")
            )
        }
    }

    private suspend fun oppdaterStatus(søknadId: UUID) =
        kotlin.runCatching {
            søknadForRiverClient.oppdaterStatus(søknadId, Status.ENDELIG_JOURNALFØRT)
        }.onSuccess {
            if (it > 0) {
                logger.info("Status på søknad sett til endelig journalført: $søknadId, it=$it")
            } else {
                logger.warn("Status er allereie sett til endelig journalført: $søknadId")
            }
        }.onFailure {
            logger.error("Failed to update søknad to endelig journalført: $søknadId")
        }.getOrThrow()
}
