package no.nav.hjelpemidler.soknad.mottak.river

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.hjelpemidler.behovsmeldingsmodell.SøknadId
import no.nav.hjelpemidler.behovsmeldingsmodell.sak.HotsakSakId
import no.nav.hjelpemidler.behovsmeldingsmodell.sak.Sakstilknytning
import no.nav.hjelpemidler.soknad.mottak.client.SøknadForRiverClient

private val log = KotlinLogging.logger {}

class HotsakOpprettet(
    rapidsConnection: RapidsConnection,
    private val søknadForRiverClient: SøknadForRiverClient,
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
        opprettKnytningMellomHotsakOgSøknad(søknadId, sakId)
    }

    private suspend fun opprettKnytningMellomHotsakOgSøknad(søknadId: SøknadId, sakId: HotsakSakId) =
        runCatching {
            søknadForRiverClient.lagreSakstilknytning(søknadId, Sakstilknytning.Hotsak(sakId))
        }.onSuccess {
            if (it > 0) {
                log.info { "Knyttet sak til søknad, sakId: $sakId, søknadId: $søknadId" }
            } else {
                log.warn { "Sak med sakId: $sakId er allerede knyttet til søknadId: $søknadId" }
            }
        }.onFailure {
            log.error(it) { "Kunne ikke knytte sammen sakId: $sakId med søknadId: $søknadId" }
        }.getOrThrow()
}
