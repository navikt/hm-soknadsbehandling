package no.nav.hjelpemidler.soknad.mottak.service

import com.github.guepardoapps.kulid.ULID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageProblems
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.hjelpemidler.soknad.mottak.db.SoknadStore
import no.nav.hjelpemidler.soknad.mottak.metrics.Prometheus
import java.time.LocalDateTime
import java.util.UUID

private val logger = KotlinLogging.logger {}
private val sikkerlogg = KotlinLogging.logger("tjenestekall")

internal class SoknadDataSink(rapidsConnection: RapidsConnection, private val store: SoknadStore) :
    River.PacketListener {

    init {
        River(rapidsConnection).apply {
            validate { it.forbid("@behandlingId") }
            validate { it.requireKey("fodselNrBruker", "fodselNrInnsender", "soknad") }
        }.register(this)
    }

    private val JsonMessage.fnrBruker get() = this["fodselNrBruker"].textValue()
    private val JsonMessage.fnrInnsender get() = this["fodselNrInnsender"].textValue()
    private val JsonMessage.soknadId get() = this["soknad"]["soknad"]["id"].textValue()

    override fun onPacket(packet: JsonMessage, context: RapidsConnection.MessageContext) {
        runBlocking {
            withContext(Dispatchers.IO) {
                launch {
                    val soknadData = SoknadData(
                        fnrBruker = packet.fnrBruker,
                        fnrInnsender = packet.fnrInnsender,
                        soknad = packet.toJson(),
                        soknadId = UUID.fromString(packet.soknadId)
                    )

                    logger.info { "Søknad mottatt: ${soknadData.soknadId}" }
                    save(soknadData)
                    // forward(soknadData, context)
                }
            }
        }
    }

    private fun save(soknadData: SoknadData) =
        kotlin.runCatching {
            store.save(soknadData)
        }.onSuccess {
            logger.info("Søknad saved: ${soknadData.soknadId}")
            Prometheus.soknadCounter.inc()
        }.onFailure {
            logger.error(it) { "Failed to save søknad: ${soknadData.soknadId}" }
        }.getOrThrow()

    private fun CoroutineScope.forward(søknadData: SoknadData, context: RapidsConnection.MessageContext) {
        launch(Dispatchers.IO + SupervisorJob()) {
            context.send(søknadData.fnrBruker, søknadData.toJson())
            Prometheus.soknadSendtCounter.inc()
        }.invokeOnCompletion {
            when (it) {
                null -> {
                    // logger.info("Søknad sent: ${søknadData.søknadsId}")
                    // sikkerlogg.info("Søknad sent med søknadsId: ${søknadData.søknadsId}, fnr: ${søknadData.fnrBruker})")
                }
                is CancellationException -> logger.warn("Cancelled: ${it.message}")
                else -> {
                    // logger.error("Failed: ${it.message}. Soknad: ${søknadData.søknadsId}")
                }
            }
        }
    }
}

internal data class SoknadData(val fnrBruker: String, val fnrInnsender: String, val soknadId: UUID, val soknad: String) {
    internal fun toJson(): String {
        return JsonMessage("{}", MessageProblems("")).also {
            it["@id"] = ULID.random()
            it["@event_name"] = "Søknad"
            it["@opprettet"] = LocalDateTime.now()
            it["fnrBruker"] = this.fnrBruker
            it["fnrInnsender"] = this.fnrInnsender
        }.toJson()
    }
}
