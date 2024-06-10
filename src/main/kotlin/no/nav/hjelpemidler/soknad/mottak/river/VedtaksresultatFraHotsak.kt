package no.nav.hjelpemidler.soknad.mottak.river

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.runBlocking
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
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
    private val søknadForRiverClient: SøknadForRiverClient,
) : PacketListenerWithOnError {

    init {
        River(rapidsConnection).apply {
            validate { it.demandValue("eventName", "hm-SøknadInnvilget") }
            validate { it.requireKey("søknadId", "fnrBruker", "vedtaksresultat", "opprettet") }
        }.register(this)
    }

    private val JsonMessage.søknadId get() = this["søknadId"].textValue()
    private val JsonMessage.fnrBruker get() = this["fnrBruker"].textValue()
    private val JsonMessage.vedtaksResultat get() = this["vedtaksresultat"].textValue()
    private val JsonMessage.vedtaksDato get() = this["opprettet"].asLocalDateTime()

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        runBlocking {
            val søknadId = UUID.fromString(packet.søknadId)
            val fnrBruker = packet.fnrBruker
            val vedtaksresultat = packet.vedtaksResultat
            val vedtaksDato = packet.vedtaksDato

            lagreVedtaksresultat(søknadId, vedtaksresultat, vedtaksDato.toLocalDate())

            val status = when (vedtaksresultat) {
                "I" -> Status.VEDTAKSRESULTAT_INNVILGET
                "IM" -> Status.VEDTAKSRESULTAT_MUNTLIG_INNVILGET
                "A" -> Status.VEDTAKSRESULTAT_AVSLÅTT
                "DI" -> Status.VEDTAKSRESULTAT_DELVIS_INNVILGET
                "HB" -> Status.VEDTAKSRESULTAT_HENLAGTBORTFALT
                else -> Status.VEDTAKSRESULTAT_ANNET
            }
            oppdaterStatus(søknadId, status)

            val vedtaksresultatLagretData = VedtaksresultatLagretData(
                søknadId,
                fnrBruker,
                vedtaksDato,
                vedtaksresultat
            )
            context.publish(fnrBruker, vedtaksresultatLagretData.toJson("hm-VedtaksresultatFraHotsakLagret", null))
        }
    }

    private suspend fun lagreVedtaksresultat(søknadId: UUID, vedtaksresultat: String, vedtaksdato: LocalDate) {
        runCatching {
            søknadForRiverClient.lagreVedtaksresultatFraHotsak(søknadId, vedtaksresultat, vedtaksdato)
        }.onSuccess {
            if (it == 0) {
                logger.warn { "Ingenting ble endret når vi forsøkte å lagre vedtaksresultat fra Hotsak for søknadId: $søknadId" }
            } else {
                logger.info { "Vedtaksresultat fra Hotsak er nå lagra for søknadId: $søknadId, vedtaksresultat: $vedtaksresultat vedtaksdato: $vedtaksdato" }
                Prometheus.vedtaksresultatLagretCounter.inc()
            }
        }.onFailure {
            logger.error(it) { "Feil under lagring av vedtaksresultat for søknadId: $søknadId" }
        }.getOrThrow()
    }

    private suspend fun oppdaterStatus(søknadId: UUID, status: Status) =
        runCatching {
            søknadForRiverClient.oppdaterStatus(søknadId, status)
        }.onSuccess {
            if (it > 0) {
                logger.info { "Status på søknad satt til: $status for søknadId: $søknadId, it: $it" }
            } else {
                logger.warn { "Status er allerede satt til: $status for søknadId: $søknadId" }
            }
        }.onFailure {
            logger.error { "Failed to update status to: $status for søknadId: $søknadId" }
        }.getOrThrow()
}
