package no.nav.hjelpemidler.soknad.mottak.service

import com.github.guepardoapps.kulid.ULID
import kotlinx.coroutines.*
import mu.KotlinLogging
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageProblems
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.hjelpemidler.soknad.mottak.db.SoknadStore
import no.nav.hjelpemidler.soknad.mottak.metrics.Prometheus
import java.time.LocalDateTime

private val logger = KotlinLogging.logger {}
private val sikkerlogg = KotlinLogging.logger("tjenestekall")

internal class SoknadDataSink(rapidsConnection: RapidsConnection, private val store: SoknadStore) :
    River.PacketListener {

    init {
        River(rapidsConnection).apply {
            validate { it.forbid("@id") }
            validate { it.requireKey("aktoerId", "brukerBehandlingId", "journalpostId") }
        }.register(this)
    }

    private val JsonMessage.fødselsnummer get() = this["aktoerId"].textValue()
    private val JsonMessage.søknadsId get() = this["brukerBehandlingId"].textValue()

    override fun onPacket(packet: JsonMessage, context: RapidsConnection.MessageContext) {
        runBlocking {
            withContext(Dispatchers.IO) {
                launch {
                    val soknadData = SoknadData(
                        fnr = packet.fødselsnummer,
                        søknadsId = packet.søknadsId,
                        soknad = packet.toJson()
                    )
                    save(soknadData)
                    forward(soknadData, context)
                }
            }
        }
    }

    private fun save(soknadData: SoknadData) =
        kotlin.runCatching {
            store.save(soknadData)
        }.onSuccess {
            logger.info("Soknad saved: ${soknadData.søknadsId}")
            Prometheus.soknadCounter.inc()
        }.onFailure {
            logger.error(it) { "Failed to save søknad: ${soknadData.søknadsId}" }
        }.getOrThrow()

    private fun CoroutineScope.forward(søknadData: SoknadData, context: RapidsConnection.MessageContext) {
        launch(Dispatchers.IO + SupervisorJob()) {
            val aktørId = "aktorId" //todo, get from json? was pdl-call in copy-pasta
            context.send(søknadData.fnr, søknadData.toJson(aktørId))
        }.invokeOnCompletion {
            when (it) {
                null -> {
                    logger.info("Søknad sent: ${søknadData.søknadsId}")
                    sikkerlogg.info("Søknad sent med søknadsId: ${søknadData.søknadsId}, fnr: ${søknadData.fnr})")
                }
                is CancellationException -> logger.warn("Cancelled: ${it.message}. Soknad: ${søknadData.søknadsId}")
                else -> {
                    logger.error("Failed: ${it.message}. Soknad: ${søknadData.søknadsId}")
                }
            }
        }
    }
}

internal data class SoknadData(val fnr: String, val søknadsId: String, val soknad: String) {
    internal fun toJson(aktørId: String): String {
        return JsonMessage("{}", MessageProblems("")).also {
            it["@id"] = ULID.random()
            it["@event_name"] = "Søknad"
            it["@opprettet"] = LocalDateTime.now()
            it["fnr"] = this.fnr
            it["aktørId"] = aktørId
            it["søknadsId"] = this.søknadsId
        }.toJson()
    }
}
internal data class SoknadJournalpostMapping(val søknadsId: String, val journalpostId: String)
