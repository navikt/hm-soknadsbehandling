package no.nav.hjelpemidler.soknad.mottak.service

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL
import mu.KotlinLogging
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helse.rapids_rivers.isMissingOrNull
import no.nav.hjelpemidler.soknad.mottak.db.SoknadStore
import no.nav.hjelpemidler.soknad.mottak.metrics.Prometheus
import java.time.LocalDateTime

private val logger = KotlinLogging.logger {}

internal class JournalPostSink(rapidsConnection: RapidsConnection, private val store: SoknadStore) : River.PacketListener {
    init {
        River(rapidsConnection).apply {
            validate { it.forbid("@id") }
            validate { it.requireKey("aktørId", "naturligIdent", "journalpostId", "behandlendeEnhet", "ferdigBehandlet", "fagsakId") }
            validate { it.interestedIn("datoRegistrert", "henvendelsestype") }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: RapidsConnection.MessageContext) {
        val datoRegistrert = if (!packet["datoRegistrert"].isMissingOrNull()) LocalDateTime.parse(packet["datoRegistrert"].textValue()) else null
        JournalPost(
            packet["journalpostId"].textValue(),
            packet["aktørId"].textValue(),
            packet["naturligIdent"].textValue(),
            packet["fagsakId"].textValue(),
            packet["behandlendeEnhet"].textValue(),
            packet["henvendelsestype"].textValue(),
            datoRegistrert
        ).apply {
            when (store.save(this)) {
                1 -> {
                    logger.info("Journalpost saved: ${this.journalpostId}")
                    Prometheus.jpCounter.inc()
                }
                else -> {
                    logger.error { "Unable to save journalpost: ${this.journalpostId}" }
                }
            }
        }
    }
}

@JsonInclude(NON_NULL)
internal data class JournalPost(
    val journalpostId: String,
    val aktørId: String,
    val naturligIdent: String,
    val fagsakId: String?,
    val behandlendeEnhet: String,
    val henvendelsestype: String,
    val datoRegistrert: LocalDateTime?,
)
