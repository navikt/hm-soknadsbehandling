package no.nav.hjelpemidler.soknad.mottak.river

import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.hjelpemidler.soknad.mottak.client.SøknadForRiverClient
import no.nav.hjelpemidler.soknad.mottak.metrics.Prometheus
import no.nav.hjelpemidler.soknad.mottak.service.Status
import no.nav.hjelpemidler.soknad.mottak.service.SøknadData
import no.nav.hjelpemidler.soknad.mottak.service.periodeMellomDatoer
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

private val logger = KotlinLogging.logger {}
private val sikkerlogg = KotlinLogging.logger("tjenestekall")

internal class GodkjennSoknad(
    rapidsConnection: RapidsConnection,
    private val søknadForRiverClient: SøknadForRiverClient,
) : PacketListenerWithOnError {

    init {
        River(rapidsConnection).apply {
            validate { it.demandValue("eventName", "godkjentAvBruker") }
            validate { it.requireKey("soknadId") }
        }.register(this)
    }

    private val JsonMessage.soknadId get() = this["soknadId"].textValue()

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        runBlocking {
            try {
                logger.info { "Bruker har godkjent søknad: ${packet.soknadId}" }
                val soknad = hentSoknadData(UUID.fromString(packet.soknadId))
                if (soknad.status != Status.VENTER_GODKJENNING) {
                    logger.info { "Søknad til godkjenning har ikke status VENTER_GODKJENNING. søknadId: ${packet.soknadId}" }
                } else {
                    loggTidBruktForGodkjenning(soknad)
                    update(UUID.fromString(packet.soknadId), Status.GODKJENT)
                    val oppdatertSoknad = hentSoknadData(UUID.fromString(packet.soknadId))
                    forward(oppdatertSoknad, context)
                }
            } catch (e: Exception) {
                logger.error(e) { "Håndtering av brukergodkjenning for søknad ${packet.soknadId} feilet" }
                throw e
            }
        }
    }

    private suspend fun update(soknadId: UUID, status: Status) =
        runCatching {
            søknadForRiverClient.oppdaterStatus(soknadId, status)
        }.onSuccess {
            logger.info("Søknad $soknadId oppdatert med status $status")
        }.onFailure {
            logger.error(it) { "Failed to update søknad $soknadId med status $status" }
        }.getOrThrow()

    private suspend fun hentSoknadData(soknadId: UUID): SøknadData =
        runCatching {
            søknadForRiverClient.hentSøknadData(soknadId)!!
        }.onFailure {
            logger.error(it) { "Failed to retrieve søknad $soknadId" }
        }.getOrThrow()

    private suspend fun loggTidBruktForGodkjenning(søknadData: SøknadData) {
        try {
            val opprettetDato = søknadForRiverClient.hentSøknadOpprettetDato(søknadData.soknadId)
            val tid = periodeMellomDatoer(
                LocalDateTime.ofInstant(opprettetDato.toInstant(), ZoneId.systemDefault()),
                LocalDateTime.now()
            )
            logger.info("Tid brukt fra opprettelse til godkjenning av søknad med søknadId: ${søknadData.soknadId} var: $tid")
        } catch (e: Exception) {
            logger.info(e) { "Klarte ikke å måle tidsbruk mellom opprettelse og godkjenning" }
        }
    }

    private fun forward(søknadData: SøknadData, context: MessageContext) {
        val fnrBruker = søknadData.fnrBruker
        val søknadId = søknadData.soknadId.toString()

        try {
            val soknadGodkjentMessage = søknadData.toJson("hm-søknadGodkjentAvBrukerMottatt")
            context.publish(fnrBruker, soknadGodkjentMessage)
            Prometheus.soknadGodkjentAvBrukerCounter.inc()
            logger.info("Søknad er godkjent av bruker: $søknadId")
            sikkerlogg.info("Søknad er godkjent med søknadId: $søknadId, fnr: $fnrBruker")
        } catch (e: Exception) {
            logger.error(e) { "Failed: ${e.message}, søknadId: $søknadId" }
        }
    }
}
