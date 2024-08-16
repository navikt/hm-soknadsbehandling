package no.nav.hjelpemidler.soknad.mottak.river

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helse.rapids_rivers.asLocalDate
import no.nav.hjelpemidler.behovsmeldingsmodell.BehovsmeldingStatus
import no.nav.hjelpemidler.behovsmeldingsmodell.sak.Vedtaksresultat
import no.nav.hjelpemidler.soknad.mottak.melding.OrdrelinjeLagretMelding
import no.nav.hjelpemidler.soknad.mottak.melding.VedtaksresultatLagretMelding
import no.nav.hjelpemidler.soknad.mottak.metrics.Metrics
import no.nav.hjelpemidler.soknad.mottak.metrics.Prometheus
import no.nav.hjelpemidler.soknad.mottak.soknadsbehandling.SøknadsbehandlingService

private val logger = KotlinLogging.logger {}

class VedtaksresultatFraInfotrygd(
    rapidsConnection: RapidsConnection,
    private val søknadsbehandlingService: SøknadsbehandlingService,
    private val metrics: Metrics,
) : AsyncPacketListener {
    init {
        River(rapidsConnection).apply {
            validate { it.demandValue("eventName", "hm-VedtaksResultatFraInfotrygd") }
            validate { it.requireKey("søknadID", "fnrBruker", "vedtaksResultat", "vedtaksDato", "soknadsType") }
        }.register(this)
    }

    private val JsonMessage.søknadId get() = uuidValue("søknadID")
    private val JsonMessage.fnrBruker get() = this["fnrBruker"].textValue()
    private val JsonMessage.vedtaksresultat get() = this["vedtaksResultat"].textValue()
    private val JsonMessage.vedtaksdato get() = this["vedtaksDato"].asLocalDate()
    private val JsonMessage.søknadstype get() = this["soknadsType"].textValue()

    override suspend fun onPacketAsync(packet: JsonMessage, context: MessageContext) {
        val søknadId = packet.søknadId
        val fnrBruker = packet.fnrBruker
        val vedtaksresultat = packet.vedtaksresultat
        val vedtaksdato = packet.vedtaksdato
        val søknadstype = packet.søknadstype

        søknadsbehandlingService.lagreVedtaksresultat(
            søknadId,
            Vedtaksresultat.Infotrygd(vedtaksresultat, vedtaksdato, søknadstype)
        )

        metrics.resultatFraInfotrygd(fnrBruker, vedtaksresultat, søknadstype)

        /*
            Sjekk om ordrelinjer kom inn før vedtaket, noe som kan skje for Infotrygd fordi vi venter med å
            hente resultatet til neste morgen. Hvis dette er tilfelle deaktiverer vi ekstern varsling for vedtaket
            og gir ekstern varsling for utsending startet i stedet...
        */
        val mottokOrdrelinjeFørVedtak = søknadsbehandlingService.harOrdreForSøknad(søknadId)

        // Lagre vedtaksstatus og send beskjed til Ditt NAV
        context.publish(
            fnrBruker,
            VedtaksresultatLagretMelding(
                søknadId = søknadId,
                fnrBruker = fnrBruker,
                vedtaksdato = vedtaksdato.atStartOfDay(),
                vedtaksresultat = vedtaksresultat,
                eksternVarslingDeaktivert = mottokOrdrelinjeFørVedtak.harOrdreAvTypeHjelpemidler,
                søknadstype = packet.søknadstype,
                eventName = "hm-VedtaksresultatLagret",
            ),
        )

        // Hvis vi allerede har ordrelinjer i databasen for denne søknaden: send utsending startet.
        if (mottokOrdrelinjeFørVedtak.harOrdreAvTypeHjelpemidler || mottokOrdrelinjeFørVedtak.harOrdreAvTypeDel) {
            søknadsbehandlingService.oppdaterStatus(søknadId, BehovsmeldingStatus.UTSENDING_STARTET)

            if (!mottokOrdrelinjeFørVedtak.harOrdreAvTypeHjelpemidler) {
                // Hvis bare ordrelinje for deler så skipper vi varsel
                return
            }

            val behovsmeldingType = søknadsbehandlingService.hentBehovsmeldingstype(søknadId)
            context.publish(fnrBruker, OrdrelinjeLagretMelding(søknadId, fnrBruker, behovsmeldingType))
            Prometheus.ordrelinjeVideresendtCounter.inc()
            logger.info { "Ordrelinje sendt ved vedtak, søknadId: $søknadId" }
        }
    }
}
