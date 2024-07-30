package no.nav.hjelpemidler.soknad.mottak.river

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.hjelpemidler.soknad.mottak.client.SøknadForRiverClient
import no.nav.hjelpemidler.soknad.mottak.logging.sikkerlogg
import no.nav.hjelpemidler.soknad.mottak.metrics.Prometheus
import no.nav.hjelpemidler.soknad.mottak.service.Status
import no.nav.hjelpemidler.soknad.mottak.service.SøknadData
import no.nav.hjelpemidler.soknad.mottak.service.periodeMellomDatoer
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

private val logger = KotlinLogging.logger {}

class GodkjennSoknad(
    rapidsConnection: RapidsConnection,
    private val søknadForRiverClient: SøknadForRiverClient,
) : AsyncPacketListener {

    init {
        River(rapidsConnection).apply {
            validate { it.demandValue("eventName", "godkjentAvBruker") }
            validate { it.requireKey("soknadId") }
        }.register(this)
    }

    private val JsonMessage.soknadId get() = uuidValue("soknadId")

    override suspend fun onPacketAsync(packet: JsonMessage, context: MessageContext) {
        try {
            logger.info { "Bruker har godkjent søknad: ${packet.soknadId}" }
            val søknad = hentSoknadData(packet.soknadId)
            if (søknad.status != Status.VENTER_GODKJENNING) {
                logger.info { "Søknad til godkjenning har ikke status VENTER_GODKJENNING. søknadId: ${packet.soknadId}" }
            } else {
                loggTidBruktForGodkjenning(søknad)
                update(packet.soknadId, Status.GODKJENT)
                val oppdatertSoknad = hentSoknadData(packet.soknadId)
                forward(oppdatertSoknad, context)
            }
        } catch (e: Exception) {
            logger.error(e) { "Håndtering av brukergodkjenning for søknadId: ${packet.soknadId} feilet" }
            throw e
        }
    }

    private suspend fun update(søknadId: UUID, status: Status) =
        runCatching {
            søknadForRiverClient.oppdaterStatus(søknadId, status)
        }.onSuccess {
            logger.info { "Søknad med søknadId: $søknadId oppdatert med status: $status" }
        }.onFailure {
            logger.error(it) { "Failed to update søknad with søknadId: $søknadId, status: $status" }
        }.getOrThrow()

    private suspend fun hentSoknadData(søknadId: UUID): SøknadData =
        runCatching {
            SøknadData(søknadForRiverClient.hentSøknad(søknadId, true))
        }.onFailure {
            logger.error(it) { "Failed to retrieve søknad with søknadId: $søknadId" }
        }.getOrThrow()

    private suspend fun loggTidBruktForGodkjenning(søknadData: SøknadData) {
        try {
            val opprettetDato = søknadForRiverClient.hentSøknad(søknadData.soknadId).søknadOpprettet
            val tid = periodeMellomDatoer(
                LocalDateTime.ofInstant(opprettetDato, ZoneId.systemDefault()),
                LocalDateTime.now()
            )
            logger.info { "Tid brukt fra opprettelse til godkjenning av søknad med søknadId: ${søknadData.soknadId} var: $tid" }
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
            logger.info { "Søknad er godkjent av bruker: $søknadId" }
            sikkerlogg.info { "Søknad er godkjent med søknadId: $søknadId, fnr: $fnrBruker" }
        } catch (e: Exception) {
            logger.error(e) { "forward() failed, søknadId: $søknadId" }
        }
    }
}
