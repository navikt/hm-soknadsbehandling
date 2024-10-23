package no.nav.hjelpemidler.soknad.mottak.river

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageProblems
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import no.nav.hjelpemidler.soknad.mottak.logging.sikkerlogg

class RiverRequiredKeyMissingException(msg: String) : Exception(msg)

interface PacketListenerWithOnError : River.PacketListener {
    override fun onError(problems: MessageProblems, context: MessageContext) {
        sikkerlogg.info { "River required keys had problems in parsing message from rapid: ${problems.toExtendedReport()}" }
        throw RiverRequiredKeyMissingException("River required keys had problems in parsing message from rapid, see Kibana Securelogs for details")
    }
}

interface AsyncPacketListener : PacketListenerWithOnError {
    override fun onPacket(packet: JsonMessage, context: MessageContext) = runBlocking(Dispatchers.IO) {
        onPacketAsync(packet, context)
    }

    suspend fun onPacketAsync(packet: JsonMessage, context: MessageContext)
}
