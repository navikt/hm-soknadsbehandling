package no.nav.hjelpemidler.soknad.mottak.service

import mu.KotlinLogging
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helse.rapids_rivers.asLocalDate
import no.nav.hjelpemidler.soknad.mottak.db.InfotrygdStore
import no.nav.hjelpemidler.soknad.mottak.metrics.Prometheus
import java.util.UUID

private val logger = KotlinLogging.logger {}
private val sikkerlogg = KotlinLogging.logger("tjenestekall")

internal class VedtaksresultatFraInfotrygd(
    rapidsConnection: RapidsConnection,
    private val infotrygdStore: InfotrygdStore
) : River.PacketListener {

    init {
        River(rapidsConnection).apply {
            validate { it.demandValue("eventName", "hm-VedtaksResultatFraInfotrygd") }
            validate { it.requireKey("søknadID", "vedtaksResultat", "vedtaksDato") }
        }.register(this)
    }

    private val JsonMessage.søknadID get() = this["søknadID"].textValue()
    private val JsonMessage.vedtaksResultat get() = this["vedtaksResultat"].textValue()
    private val JsonMessage.vedtaksDato get() = this["vedtaksDato"].asLocalDate()

    override fun onPacket(packet: JsonMessage, context: RapidsConnection.MessageContext) {
        val søknadsId = UUID.fromString(packet.søknadID)
        if (søknadsId.toString() == "6b6f88b4-48eb-4b67-8c67-161240ddadc7") {
            logger.info { "Hopper over event i skip-list: $søknadsId" }
            return
        }

        kotlin.runCatching {
            infotrygdStore.lagreVedtaksresultat(søknadsId, packet.vedtaksResultat, packet.vedtaksDato)
        }.onSuccess {
            if (it == 0) {
                logger.warn("Ingenting ble endret når vi forsøkte å lagre vedtaksresultat for søknadsId=$søknadsId")
            } else {
                logger.info("Vedtaksresultat er nå lagra for søknadsId=$søknadsId")
                Prometheus.vedtaksresultatLagretCounter.inc()
            }
        }.onFailure {
            logger.error(it) { "Feil under lagring av vedtaksresultat for søknadsId=$søknadsId" }
        }.getOrThrow()
    }
}
