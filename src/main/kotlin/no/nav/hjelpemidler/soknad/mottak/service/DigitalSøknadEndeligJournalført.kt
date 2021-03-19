package no.nav.hjelpemidler.soknad.mottak.service

import mu.KotlinLogging
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.hjelpemidler.soknad.mottak.db.SøknadStore
import java.util.UUID

private val logger = KotlinLogging.logger {}

internal class DigitalSøknadEndeligJournalført(rapidsConnection: RapidsConnection, private val store: SøknadStore) :
    River.PacketListener {

    init {
        River(rapidsConnection).apply {
            validate { it.demandValue("eventName", "DigitalSoeknadEndeligJournalfoert") }
            validate { it.requireKey("soknadId") }
        }.register(this)
    }

    private val JsonMessage.søknadId get() = this["soknadId"].textValue()

    override fun onPacket(packet: JsonMessage, context: RapidsConnection.MessageContext) {
        val søknadsId = UUID.fromString(packet.søknadId)

        kotlin.runCatching {
            store.oppdaterStatus(søknadsId, Status.ENDELIG_JOURNALFØRT)
        }.onSuccess {
            logger.info("Søknad updated to endelig journalført: $søknadsId")
        }.onFailure {
            logger.error("Failed to update søknad to endelig journalført: $søknadsId")
        }.getOrThrow()
    }
}
