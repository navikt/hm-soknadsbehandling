package no.nav.hjelpemidler.soknad.mottak.river

import com.github.guepardoapps.kulid.ULID
import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.MessageProblems
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.hjelpemidler.soknad.mottak.client.SøknadForRiverClient
import no.nav.hjelpemidler.soknad.mottak.logging.sikkerlogg
import no.nav.hjelpemidler.soknad.mottak.metrics.Prometheus
import no.nav.hjelpemidler.soknad.mottak.service.Status
import java.time.LocalDateTime
import java.util.UUID

private val logger = KotlinLogging.logger {}

class SlettSoknad(
    rapidsConnection: RapidsConnection,
    private val søknadForRiverClient: SøknadForRiverClient,
) : AsyncPacketListener {
    init {
        River(rapidsConnection).apply {
            validate { it.demandValue("eventName", "slettetAvBruker") }
            validate { it.requireKey("soknadId") }
        }.register(this)
    }

    private val JsonMessage.søknadId: UUID get() = uuidValue("soknadId")

    override suspend fun onPacketAsync(packet: JsonMessage, context: MessageContext) {
        try {
            logger.info { "Bruker har slettet søknad: ${packet.søknadId}" }
            val rowsUpdated = update(packet.søknadId, Status.SLETTET)
            if (rowsUpdated > 0) {
                val fnrBruker = søknadForRiverClient.hentSøknad(packet.søknadId).fnrBruker
                forward(packet.søknadId, fnrBruker, context)
            } else {
                logger.info { "Søknad som slettes er allerede slettet eller stod ikke til godkjenning, søknadId: ${packet.søknadId}" }
            }
        } catch (e: Exception) {
            throw RuntimeException("Håndtering av brukers sletting av søknad ${packet.søknadId} feilet", e)
        }
    }

    private suspend fun update(søknadId: UUID, status: Status) =
        runCatching {
            søknadForRiverClient.slettSøknad(søknadId)
        }.onSuccess {
            logger.info { "Søknad $søknadId oppdatert med status $status" }
        }.onFailure {
            logger.error(it) { "Failed to update søknad $søknadId med status $status" }
        }.getOrThrow()

    private fun forward(søknadId: UUID, fnrBruker: String, context: MessageContext) {
        try {
            val soknadGodkjentMessage = JsonMessage("{}", MessageProblems("")).also {
                it["@id"] = ULID.random()
                it["@event_name"] = "SøknadSlettetAvBruker"
                it["@opprettet"] = LocalDateTime.now()
                it["fodselNrBruker"] = fnrBruker
                it["soknadId"] = søknadId.toString()
            }.toJson()
            context.publish(fnrBruker, soknadGodkjentMessage)
            Prometheus.soknadSlettetAvBrukerCounter.inc()
            logger.info { "Søknad er slettet av bruker: $søknadId" }
            sikkerlogg.info { "Søknad er slettet med søknadId: $søknadId, fnr: $fnrBruker" }
        } catch (e: Exception) {
            logger.error(e) { "forward() failed, søknadId: $søknadId" }
        }
    }
}
