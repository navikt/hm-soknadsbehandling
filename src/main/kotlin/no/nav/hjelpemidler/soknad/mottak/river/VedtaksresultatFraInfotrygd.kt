package no.nav.hjelpemidler.soknad.mottak.river

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.runBlocking
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helse.rapids_rivers.asLocalDate
import no.nav.hjelpemidler.soknad.mottak.client.SøknadForRiverClient
import no.nav.hjelpemidler.soknad.mottak.metrics.Metrics
import no.nav.hjelpemidler.soknad.mottak.metrics.Prometheus
import no.nav.hjelpemidler.soknad.mottak.service.BehovsmeldingType
import no.nav.hjelpemidler.soknad.mottak.service.OrdrelinjeData
import no.nav.hjelpemidler.soknad.mottak.service.Status
import no.nav.hjelpemidler.soknad.mottak.service.VedtaksresultatLagretData
import java.time.LocalDate
import java.util.UUID

private val logger = KotlinLogging.logger {}
private val sikkerlogg = KotlinLogging.logger("tjenestekall")

internal class VedtaksresultatFraInfotrygd(
    rapidsConnection: RapidsConnection,
    private val søknadForRiverClient: SøknadForRiverClient,
    private val metrics: Metrics,
) : PacketListenerWithOnError {

    init {
        River(rapidsConnection).apply {
            validate { it.demandValue("eventName", "hm-VedtaksResultatFraInfotrygd") }
            validate { it.requireKey("søknadID", "fnrBruker", "vedtaksResultat", "vedtaksDato", "soknadsType") }
        }.register(this)
    }

    private val JsonMessage.søknadId get() = this["søknadID"].textValue()
    private val JsonMessage.fnrBruker get() = this["fnrBruker"].textValue()
    private val JsonMessage.vedtaksResultat get() = this["vedtaksResultat"].textValue()
    private val JsonMessage.vedtaksDato get() = this["vedtaksDato"].asLocalDate()
    private val JsonMessage.soknadsType get() = this["soknadsType"].textValue()

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        runBlocking {
            val søknadId = UUID.fromString(packet.søknadId)
            val fnrBruker = packet.fnrBruker
            val vedtaksresultat = packet.vedtaksResultat
            val vedtaksdato = packet.vedtaksDato
            val soknadsType = packet.soknadsType

            lagreVedtaksresultat(søknadId, vedtaksresultat, vedtaksdato, soknadsType)

            metrics.resultatFraInfotrygd(fnrBruker, vedtaksresultat, soknadsType)

            val status = when (vedtaksresultat) {
                "I" -> Status.VEDTAKSRESULTAT_INNVILGET
                "IM" -> Status.VEDTAKSRESULTAT_MUNTLIG_INNVILGET
                "A" -> Status.VEDTAKSRESULTAT_AVSLÅTT
                "DI" -> Status.VEDTAKSRESULTAT_DELVIS_INNVILGET
                "HB" -> Status.VEDTAKSRESULTAT_HENLAGTBORTFALT
                else -> Status.VEDTAKSRESULTAT_ANNET
            }
            oppdaterStatus(søknadId, status)

            // Sjekk om ordrelinjer kom inn før vedtaket, noe som kan skje for Infotrygd fordi vi venter med å
            // hente resultatet til neste morgen. Hvis dette er tilfelle deaktiverer vi ekstern varsling for vedtaket
            // og gir ekstern varsling for utsending startet i stede...
            val mottokOrdrelinjeFørVedtak = søknadForRiverClient.harOrdreForSøknad(søknadId)

            // Lagre vedtaksstatus og send beskjed til ditt nav
            val vedtaksresultatLagretData = VedtaksresultatLagretData(
                søknadId,
                fnrBruker,
                vedtaksdato.atStartOfDay(),
                vedtaksresultat,
                mottokOrdrelinjeFørVedtak.harOrdreAvTypeHjelpemidler
            )
            context.publish(fnrBruker, vedtaksresultatLagretData.toJson("hm-VedtaksresultatLagret", packet.soknadsType))

            // Hvis vi allerede har ordrelinjer i databasen for denne søknaden: send utsending startet.
            if (mottokOrdrelinjeFørVedtak.harOrdreAvTypeHjelpemidler || mottokOrdrelinjeFørVedtak.harOrdreAvTypeDel) {
                oppdaterStatus(søknadId, Status.UTSENDING_STARTET)

                if (!mottokOrdrelinjeFørVedtak.harOrdreAvTypeHjelpemidler) {
                    // Hvis bare ordrelinje for deler så skipper vi varsel
                    return@runBlocking
                }

                val ordrelinjeData = OrdrelinjeData(
                    søknadId = søknadId,
                    behovsmeldingType = søknadForRiverClient.behovsmeldingTypeFor(søknadId)
                        ?: BehovsmeldingType.SØKNAD,
                    fnrBruker = fnrBruker,
                    // Resten av feltene brukes ikke i json:
                    oebsId = 0,
                    serviceforespørsel = null,
                    ordrenr = 0,
                    ordrelinje = 0,
                    delordrelinje = 0,
                    artikkelnr = "",
                    antall = 0.0,
                    enhet = "",
                    produktgruppe = "",
                    produktgruppeNr = "",
                    hjelpemiddeltype = "",
                    data = null,
                )
                context.publish(ordrelinjeData.fnrBruker, ordrelinjeData.toJson("hm-OrdrelinjeLagret"))
                Prometheus.ordrelinjeLagretOgSendtTilRapidCounter.inc()
                logger.info { "Ordrelinje sendt ved vedtak: ${ordrelinjeData.søknadId}" }
            }
        }
    }

    private suspend fun lagreVedtaksresultat(
        søknadId: UUID,
        vedtaksresultat: String,
        vedtaksdato: LocalDate,
        søknadstype: String,
    ) {
        runCatching {
            søknadForRiverClient.lagreVedtaksresultat(søknadId, vedtaksresultat, vedtaksdato, søknadstype)
        }.onSuccess {
            if (it == 0) {
                logger.warn { "Ingenting ble endret når vi forsøkte å lagre vedtaksresultat for søknadId: $søknadId" }
            } else {
                logger.info { "Vedtaksresultat er nå lagret for søknadId: $søknadId vedtaksresultat: $vedtaksresultat vedtaksdato: $vedtaksdato" }
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
            logger.error(it) { "Failed to update status to: $status for søknadId: $søknadId" }
        }.getOrThrow()
}
