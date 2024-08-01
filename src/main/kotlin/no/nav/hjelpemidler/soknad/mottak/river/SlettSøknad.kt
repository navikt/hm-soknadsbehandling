package no.nav.hjelpemidler.soknad.mottak.river

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.MessageProblems
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.hjelpemidler.behovsmeldingsmodell.SøknadId
import no.nav.hjelpemidler.soknad.mottak.logging.sikkerlogg
import no.nav.hjelpemidler.soknad.mottak.metrics.Prometheus
import no.nav.hjelpemidler.soknad.mottak.soknadsbehandling.SøknadsbehandlingService
import java.time.LocalDateTime
import java.util.UUID

private val log = KotlinLogging.logger {}

class SlettSøknad(
    rapidsConnection: RapidsConnection,
    private val søknadsbehandlingService: SøknadsbehandlingService,
) : AsyncPacketListener {
    init {
        River(rapidsConnection).apply {
            validate { it.demandValue("eventName", "slettetAvBruker") }
            validate { it.requireKey("soknadId") }
        }.register(this)
    }

    private val JsonMessage.søknadId: SøknadId get() = uuidValue("soknadId")

    override suspend fun onPacketAsync(packet: JsonMessage, context: MessageContext) {
        val søknadId = packet.søknadId
        try {
            if (søknadsbehandlingService.slettSøknad(søknadId)) {
                val fnrBruker = søknadsbehandlingService.hentSøknad(søknadId).fnrBruker
                val message = JsonMessage("{}", MessageProblems("")).also {
                    it["@id"] = UUID.randomUUID()
                    it["@event_name"] = "SøknadSlettetAvBruker"
                    it["@opprettet"] = LocalDateTime.now()
                    it["fodselNrBruker"] = fnrBruker
                    it["soknadId"] = søknadId.toString()
                }.toJson()
                context.publish(fnrBruker, message)
                Prometheus.søknadSlettetAvBrukerCounter.inc()
                log.info { "Søknad er slettet av bruker: $søknadId" }
                sikkerlogg.info { "Søknad er slettet med søknadId: $søknadId, fnr: $fnrBruker" }
            }
        } catch (e: Exception) {
            log.error(e) { "Håndtering av brukers sletting av søknadId: $søknadId feilet" }
            throw e
        }
    }
}
