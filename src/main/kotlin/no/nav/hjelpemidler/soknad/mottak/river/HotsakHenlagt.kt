package no.nav.hjelpemidler.soknad.mottak.river

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers.asLocalDateTime
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.hjelpemidler.behovsmeldingsmodell.sak.HotsakSakId
import no.nav.hjelpemidler.behovsmeldingsmodell.sak.Vedtaksresultat
import no.nav.hjelpemidler.soknad.mottak.soknadsbehandling.SøknadsbehandlingService

private val log = KotlinLogging.logger {}

class HotsakHenlagt(
    rapidsConnection: RapidsConnection,
    private val søknadsbehandlingService: SøknadsbehandlingService,
) : AsyncPacketListener {
    init {
        River(rapidsConnection).apply {
            validate { it.demandValue("eventName", "hm-SakHenlagt") }
            validate {
                it.requireKey(
                    "søknadId",
                    "sakId",
                    "fnrBruker",
                    "henleggelsesdato",
                    "henleggelsesårsak",
                )
            }
        }.register(this)
    }

    private val JsonMessage.søknadId get() = uuidValue("søknadId")
    private val JsonMessage.sakId get() = HotsakSakId(this["sakId"].textValue())
    private val JsonMessage.fnrBruker get() = this["fnrBruker"].textValue()
    private val JsonMessage.henleggelsesdato get() = this["henleggelsesdato"].asLocalDateTime()
    private val JsonMessage.henleggelsesårsak get() = this["henleggelsesårsak"].textValue()

    override suspend fun onPacketAsync(packet: JsonMessage, context: MessageContext) {
        val søknadId = packet.søknadId
        val sakId = packet.sakId
        log.info { "Sak henlagt i Hotsak, sakId: $sakId, søknadId: $søknadId" }
        søknadsbehandlingService.lagreVedtaksresultat(
            søknadId,
            Vedtaksresultat.Hotsak("HB", packet.henleggelsesdato.toLocalDate())
        )
    }
}
