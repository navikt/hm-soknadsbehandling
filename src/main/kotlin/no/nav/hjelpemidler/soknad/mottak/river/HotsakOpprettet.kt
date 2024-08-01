package no.nav.hjelpemidler.soknad.mottak.river

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.hjelpemidler.behovsmeldingsmodell.sak.HotsakSakId
import no.nav.hjelpemidler.behovsmeldingsmodell.sak.Sakstilknytning
import no.nav.hjelpemidler.soknad.mottak.soknadsbehandling.SøknadsbehandlingService

private val log = KotlinLogging.logger {}

class HotsakOpprettet(
    rapidsConnection: RapidsConnection,
    private val søknadsbehandlingService: SøknadsbehandlingService,
) : AsyncPacketListener {
    init {
        River(rapidsConnection).apply {
            validate { it.demandValue("eventName", "hm-sakOpprettet") }
            validate { it.requireKey("soknadId", "sakId") }
        }.register(this)
    }

    private val JsonMessage.søknadId get() = uuidValue("soknadId")
    private val JsonMessage.sakId get() = HotsakSakId(this["sakId"].textValue())

    override suspend fun onPacketAsync(packet: JsonMessage, context: MessageContext) {
        val søknadId = packet.søknadId
        val sakId = packet.sakId
        log.info { "Sak for søknadId: $søknadId opprettet i Hotsak, sakId: $sakId" }
        søknadsbehandlingService.lagreSakstilknytning(søknadId, Sakstilknytning.Hotsak(sakId))
    }
}
