package no.nav.hjelpemidler.soknad.mottak.river

import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helse.rapids_rivers.asLocalDateTime
import no.nav.hjelpemidler.soknad.mottak.asObject
import no.nav.hjelpemidler.soknad.mottak.client.SøknadForRiverClient
import no.nav.hjelpemidler.soknad.mottak.service.BestillingAvvistLagretData
import no.nav.hjelpemidler.soknad.mottak.service.Status
import java.util.UUID

private val logger = KotlinLogging.logger {}

internal class BestillingAvvistFraHotsak(
    rapidsConnection: RapidsConnection,
    private val søknadForRiverClient: SøknadForRiverClient
) : PacketListenerWithOnError {

    init {
        River(rapidsConnection).apply {
            validate { it.demandValue("eventName", "hm-BestillingAvvist") }
            validate { it.requireKey("søknadId", "fodselsnummer", "opprettet", "valgte_arsaker", "begrunnelse") }
        }.register(this)
    }

    private val JsonMessage.søknadId get() = this["søknadId"].textValue()
    private val JsonMessage.fnrBruker get() = this["fodselsnummer"].textValue()
    private val JsonMessage.opprettet get() = this["opprettet"].asLocalDateTime()
    private val JsonMessage.valgtÅrsaker get() = this["valgte_arsaker"].asObject<Set<String>>()
    private val JsonMessage.begrunnelse get() = this["begrunnelse"].textValue()

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        runBlocking {
            val søknadId = UUID.fromString(packet.søknadId)
            val fnrBruker = packet.fnrBruker
            val opprettet = packet.opprettet
            val begrunnelse = packet.begrunnelse
            val valgtÅrsaker = packet.valgtÅrsaker
            oppdaterStatus(
                søknadId,
                Status.BESTILLING_AVVIST,
                valgtÅrsaker,
                begrunnelse
            )

            val bestillingAvvistLagretData = BestillingAvvistLagretData(
                søknadId,
                fnrBruker,
                opprettet,
                begrunnelse,
                valgtÅrsaker.toList(),
            )

            context.publish(fnrBruker, bestillingAvvistLagretData.toJson("hm-BestillingAvvistFraHotsakLagret"))
        }
    }

    private suspend fun oppdaterStatus(
        søknadId: UUID,
        status: Status,
        valgteÅrsaker: Set<String>,
        begrunnelse: String
    ) =
        kotlin.runCatching {
            søknadForRiverClient.oppdaterStatus(StatusMedÅrsak(søknadId, status, valgteÅrsaker, begrunnelse))
        }.onSuccess {
            if (it > 0) {
                logger.info("Status på bestilling sett til $status for søknadId $søknadId, it=$it")
            } else {
                logger.warn("Status er allereie sett til $status for søknadId $søknadId")
            }
        }.onFailure {
            logger.error("Failed to update status to $status for søknadId $søknadId")
        }.getOrThrow()
}

internal data class StatusMedÅrsak(
    val søknadId: UUID,
    val status: Status,
    val valgteÅrsaker: Set<String>?,
    val begrunnelse: String?
)
