package no.nav.hjelpemidler.soknad.mottak.river

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers.asLocalDateTime
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.hjelpemidler.behovsmeldingsmodell.sak.Vedtaksresultat
import no.nav.hjelpemidler.soknad.mottak.melding.VedtaksresultatLagretMelding
import no.nav.hjelpemidler.soknad.mottak.soknadsbehandling.SøknadsbehandlingService

private val log = KotlinLogging.logger {}

class VedtaksresultatFraHotsak(
    rapidsConnection: RapidsConnection,
    private val søknadsbehandlingService: SøknadsbehandlingService,
) : AsyncPacketListener {
    init {
        River(rapidsConnection).apply {
            precondition { it.requireValue("eventName", "hm-vedtak-fattet") }
            validate { it.requireKey("søknadId", "fnrBruker", "utfall", "opprettet") }
        }.register(this)
    }

    private val JsonMessage.søknadId get() = uuidValue("søknadId")
    private val JsonMessage.fnrBruker get() = this["fnrBruker"].stringValue()
    private val JsonMessage.utfall get() = this["utfall"].stringValue()
    private val JsonMessage.vedtaksdato get() = this["opprettet"].asLocalDateTime()

    override suspend fun onPacketAsync(packet: JsonMessage, context: MessageContext) {
        val søknadId = packet.søknadId
        val fnrBruker = packet.fnrBruker
        val utfall = packet.utfall
        val vedtaksdato = packet.vedtaksdato

        søknadsbehandlingService.lagreVedtaksresultat(
            søknadId,
            Vedtaksresultat.Hotsak(utfall, vedtaksdato.toLocalDate())
        )

        context.publish(
            fnrBruker,
            VedtaksresultatLagretMelding(
                søknadId = søknadId,
                fnrBruker = fnrBruker,
                vedtaksdato = vedtaksdato,
                vedtaksresultat = utfall,
                eksternVarslingDeaktivert = false,
                søknadstype = null,
                eventName = "hm-VedtaksresultatFraHotsakLagret"
            ),
        )
    }
}
