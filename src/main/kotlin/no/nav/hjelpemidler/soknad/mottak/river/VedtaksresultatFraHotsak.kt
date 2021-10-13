package no.nav.hjelpemidler.soknad.mottak.river

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helse.rapids_rivers.asLocalDate
import no.nav.helse.rapids_rivers.asLocalDateTime
import no.nav.hjelpemidler.soknad.mottak.client.SøknadForRiverClient
import no.nav.hjelpemidler.soknad.mottak.metrics.Prometheus
import no.nav.hjelpemidler.soknad.mottak.service.Status
import no.nav.hjelpemidler.soknad.mottak.service.VedtaksresultatLagretData
import java.time.LocalDate
import java.util.UUID

private val logger = KotlinLogging.logger {}
private val sikkerlogg = KotlinLogging.logger("tjenestekall")

internal class VedtaksresultatFraHotsak(
    rapidsConnection: RapidsConnection,
    private val søknadForRiverClient: SøknadForRiverClient
) : PacketListenerWithOnError {

    init {
        River(rapidsConnection).apply {
            validate { it.demandValue("eventName", "hm-SøknadInnvilget") }
            validate { it.requireKey("søknadId", "fnrBruker", "vedtaksresultat", "opprettet") }
        }.register(this)
    }

    private val JsonMessage.søknadID get() = this["søknadId"].textValue()
    private val JsonMessage.fnrBruker get() = this["fnrBruker"].textValue()
    private val JsonMessage.vedtaksResultat get() = this["vedtaksresultat"].textValue()
    private val JsonMessage.vedtaksDato get() = this["opprettet"].asLocalDateTime()

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        runBlocking {
            withContext(Dispatchers.IO) {
                launch {

                    val søknadsId = UUID.fromString(packet.søknadID)

                    lagreVedtaksresultat(søknadsId, packet.vedtaksResultat, packet.vedtaksDato.toLocalDate())

                    val status = when (packet.vedtaksResultat) {
                        "I" -> Status.VEDTAKSRESULTAT_INNVILGET
                        "IM" -> Status.VEDTAKSRESULTAT_MUNTLIG_INNVILGET
                        "A" -> Status.VEDTAKSRESULTAT_AVSLÅTT
                        "DI" -> Status.VEDTAKSRESULTAT_DELVIS_INNVILGET
                        else -> Status.VEDTAKSRESULTAT_ANNET
                    }
                    oppdaterStatus(søknadsId, status)

                    val vedtaksresultatLagretData =
                        VedtaksresultatLagretData(søknadsId, packet.fnrBruker, packet.vedtaksResultat)
                    context.publish(packet.fnrBruker, vedtaksresultatLagretData.toJson("hm-VedtaksresultatFraHotsakLagret"))
                }
            }
        }
    }

    private suspend fun lagreVedtaksresultat(søknadsId: UUID, vedtaksresultat: String, vedtaksdato: LocalDate) {
        kotlin.runCatching {
            søknadForRiverClient.lagreVedtaksresultatFraHotsak(søknadsId, vedtaksresultat, vedtaksdato)
        }.onSuccess {
            if (it == 0) {
                logger.warn("Ingenting ble endret når vi forsøkte å lagre vedtaksresultat fra HOTSAK for søknadsId=$søknadsId")
            } else {
                logger.info("Vedtaksresultat fra HOTSAK er nå lagra for søknadsId=$søknadsId vedtaksResultat=$vedtaksresultat vedtaksDato=$vedtaksdato")
                Prometheus.vedtaksresultatLagretCounter.inc()
            }
        }.onFailure {
            logger.error(it) { "Feil under lagring av vedtaksresultat for søknadsId=$søknadsId" }
        }.getOrThrow()
    }

    private suspend fun oppdaterStatus(søknadsId: UUID, status: Status) =
        kotlin.runCatching {
            søknadForRiverClient.oppdaterStatus(søknadsId, status)
        }.onSuccess {
            if (it > 0) {
                logger.info("Status på søknad sett til $status for søknadId $søknadsId, it=$it")
            } else {
                logger.warn("Status er allereie sett til $status for søknadId $søknadsId")
            }
        }.onFailure {
            logger.error("Failed to update status to $status for søknadId $søknadsId")
        }.getOrThrow()
}
