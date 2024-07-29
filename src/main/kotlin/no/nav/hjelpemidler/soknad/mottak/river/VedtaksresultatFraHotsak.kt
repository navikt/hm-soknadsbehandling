package no.nav.hjelpemidler.soknad.mottak.river

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helse.rapids_rivers.asLocalDateTime
import no.nav.hjelpemidler.behovsmeldingsmodell.sak.Vedtaksresultat
import no.nav.hjelpemidler.soknad.mottak.client.SøknadForRiverClient
import no.nav.hjelpemidler.soknad.mottak.metrics.Prometheus
import no.nav.hjelpemidler.soknad.mottak.service.VedtaksresultatLagretData
import java.time.LocalDate
import java.util.UUID

private val logger = KotlinLogging.logger {}

class VedtaksresultatFraHotsak(
    rapidsConnection: RapidsConnection,
    private val søknadForRiverClient: SøknadForRiverClient,
) : AsyncPacketListener {
    init {
        River(rapidsConnection).apply {
            validate { it.demandValue("eventName", "hm-SøknadInnvilget") }
            validate { it.requireKey("søknadId", "fnrBruker", "vedtaksresultat", "opprettet") }
        }.register(this)
    }

    private val JsonMessage.søknadId get() = uuidValue("søknadId")
    private val JsonMessage.fnrBruker get() = this["fnrBruker"].textValue()
    private val JsonMessage.vedtaksresultat get() = this["vedtaksresultat"].textValue()
    private val JsonMessage.vedtaksdato get() = this["opprettet"].asLocalDateTime()

    override suspend fun onPacketAsync(packet: JsonMessage, context: MessageContext) {
        val søknadId = packet.søknadId
        val fnrBruker = packet.fnrBruker
        val vedtaksresultat = packet.vedtaksresultat
        val vedtaksdato = packet.vedtaksdato

        lagreVedtaksresultat(søknadId, vedtaksresultat, vedtaksdato.toLocalDate())

        val vedtaksresultatLagretData = VedtaksresultatLagretData(
            søknadId,
            fnrBruker,
            vedtaksdato,
            vedtaksresultat
        )
        context.publish(fnrBruker, vedtaksresultatLagretData.toJson("hm-VedtaksresultatFraHotsakLagret", null))
    }

    private suspend fun lagreVedtaksresultat(søknadId: UUID, vedtaksresultat: String, vedtaksdato: LocalDate) {
        runCatching {
            søknadForRiverClient.lagreVedtaksresultat(søknadId, Vedtaksresultat.Hotsak(vedtaksresultat, vedtaksdato))
        }.onSuccess {
            if (it == 0) {
                logger.warn { "Ingenting ble endret når vi forsøkte å lagre vedtaksresultat fra Hotsak for søknadId: $søknadId" }
            } else {
                logger.info { "Vedtaksresultat fra Hotsak er nå lagret for søknadId: $søknadId, vedtaksresultat: $vedtaksresultat, vedtaksdato: $vedtaksdato" }
                Prometheus.vedtaksresultatLagretCounter.inc()
            }
        }.onFailure {
            logger.error(it) { "Feil under lagring av vedtaksresultat fra Hotsak for søknadId: $søknadId" }
        }.getOrThrow()
    }
}
