package no.nav.hjelpemidler.soknad.mottak.river

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageProblems
import io.github.oshai.kotlinlogging.KotlinLogging
import io.micrometer.core.instrument.MeterRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import no.nav.hjelpemidler.logging.teamInfo

private val log = KotlinLogging.logger {}

interface AsyncPacketListener : River.PacketListener {
    override fun onPacket(
        packet: JsonMessage,
        context: MessageContext,
        metadata: MessageMetadata,
        meterRegistry: MeterRegistry,
    ) = runBlocking(Dispatchers.IO) {
        onPacketAsync(packet, context)
    }

    suspend fun onPacketAsync(packet: JsonMessage, context: MessageContext)

    override fun onError(
        problems: MessageProblems,
        context: MessageContext,
        metadata: MessageMetadata,
    ) {
        val message = "Validering av melding feilet, se Team Logs for detaljer"
        log.info { message }
        log.teamInfo { "Validering av melding feilet: '${problems.toExtendedReport()}'" }
        error(message)
    }
}
