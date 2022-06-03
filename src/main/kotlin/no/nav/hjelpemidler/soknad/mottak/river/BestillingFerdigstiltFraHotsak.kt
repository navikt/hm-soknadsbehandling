package no.nav.hjelpemidler.soknad.mottak.river

import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.hjelpemidler.soknad.mottak.client.SøknadForRiverClient
import no.nav.hjelpemidler.soknad.mottak.service.Status
import java.util.UUID

private val logger = KotlinLogging.logger {}

internal class BestillingFerdigstiltFraHotsak(
    rapidsConnection: RapidsConnection,
    private val søknadForRiverClient: SøknadForRiverClient,
) : PacketListenerWithOnError {
    companion object {
        private val logger = KotlinLogging.logger {}
    }

    init {
        River(rapidsConnection).apply {
            validate { it.demandValue("eventName", "hm-BestillingFerdigstilt") }
            validate { it.requireKey("søknadId", "fodselsnummer", "opprettet") }
        }.register(this)
    }

    private val JsonMessage.søknadId get() = this["søknadId"].textValue()

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        runBlocking {
            val søknadId = packet.søknadId
            oppdaterStatus(UUID.fromString(søknadId), Status.BESTILLING_FERDIGSTILT)

            // context.publish(fnrBruker, vedtaksresultatLagretData.toJson("hm-VedtaksresultatFraHotsakLagret"))
        }
    }

    private suspend fun oppdaterStatus(søknadId: UUID, status: Status) =
        kotlin.runCatching {
            søknadForRiverClient.oppdaterStatus(søknadId, status)
        }.onSuccess {
            if (it > 0) {
                logger.info("Status på bestilling satt til $status for søknadId $søknadId, it=$it")
            } else {
                logger.warn("Status er allereie sett til $status for søknadId $søknadId")
            }
        }.onFailure {
            logger.error("Failed to update status to $status for søknadId $søknadId")
        }.getOrThrow()
}
