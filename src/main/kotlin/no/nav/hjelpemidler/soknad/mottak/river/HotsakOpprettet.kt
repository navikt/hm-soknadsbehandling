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

internal class HotsakOpprettet(
    rapidsConnection: RapidsConnection,
    private val søknadForRiverClient: SøknadForRiverClient,
) : PacketListenerWithOnError {
    init {
        River(rapidsConnection).apply {
            validate { it.demandValue("eventName", "hm-sakOpprettet") }
            validate { it.requireKey("soknadId", "sakId") }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        logger.info {
            "Received sak-opprettet event with søknadId: ${packet["soknadId"].asText()} and sakId: ${packet["sakId"].asText()}"
        }

        val søknadId = packet["soknadId"].asText()
        val sakId = packet["sakId"].asText()

        runBlocking {
            opprettKnytningMellomHotsakOgSøknad(UUID.fromString(søknadId), sakId)
        }
    }

    private suspend fun opprettKnytningMellomHotsakOgSøknad(søknadId: UUID, sakId: String) =
        runCatching {
            søknadForRiverClient.lagKnytningMellomHotsakOgSøknad(søknadId, sakId)
        }.onSuccess {
            if (it > 0) {
                logger.info("Knyttet sak til søknad, sakId: $sakId, søknadId: $søknadId")
            } else {
                logger.warn("Sak med sakId: $sakId er allerede knyttet til søknadId: $søknadId")
            }
        }.onFailure {
            logger.error("Kunne ikke knytte sammen sakId: $sakId med søknadId: $søknadId", it)
        }.getOrThrow()
}
