package no.nav.hjelpemidler.soknad.mottak.river

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.hjelpemidler.soknad.mottak.client.SøknadForRiverClient
import java.util.UUID

private val logger = KotlinLogging.logger {}
private val sikkerlogg = KotlinLogging.logger("tjenestekall")

internal class HotsakOpprettet(
    rapidsConnection: RapidsConnection,
    private val søknadForRiverClient: SøknadForRiverClient
) : PacketListenerWithOnError {

    init {
        River(rapidsConnection).apply {
            validate { it.demandValue("eventName", "hm-sakOpprettet") }
            validate { it.requireKey("soknadId", "sakId") }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        logger.info(
            """Received sak-opprettet event with søknadId: ${packet["soknadId"].asText()} 
                and sakId: ${packet["sakId"].asText()}
                     """.trimMargin()
        )

        val søknadId = packet["soknadId"].asText()
        val sakId = packet["sakId"].asText()

        runBlocking {
            withContext(Dispatchers.IO) {
                launch {
                    opprettKnytningMellomHotsakOgSøknad(UUID.fromString(søknadId), sakId)
                }
            }
        }
    }

    private suspend fun opprettKnytningMellomHotsakOgSøknad(søknadId: UUID, sakId: String) =
        kotlin.runCatching {
            søknadForRiverClient.lagKnytningMellomHotsakOgSøknad(søknadId, sakId)
        }.onSuccess {
            if (it > 0) {
                logger.info("Knytta sak: $sakId til søknad: $søknadId")
            } else {
                logger.warn("Sak: $sakId er allerede knytta til søknad: $søknadId")
            }
        }.onFailure {
            logger.error("Failed to knytte sammen sak: $sakId med søknad: $søknadId")
        }.getOrThrow()
}
