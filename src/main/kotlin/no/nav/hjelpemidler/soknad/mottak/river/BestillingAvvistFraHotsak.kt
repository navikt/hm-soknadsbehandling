package no.nav.hjelpemidler.soknad.mottak.river

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers.asLocalDateTime
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import no.nav.hjelpemidler.behovsmeldingsmodell.BehovsmeldingStatus
import no.nav.hjelpemidler.behovsmeldingsmodell.Statusendring
import no.nav.hjelpemidler.behovsmeldingsmodell.SøknadId
import no.nav.hjelpemidler.behovsmeldingsmodell.TilknyttetSøknad
import no.nav.hjelpemidler.soknad.mottak.asObject
import no.nav.hjelpemidler.soknad.mottak.melding.Melding
import no.nav.hjelpemidler.soknad.mottak.soknadsbehandling.SøknadsbehandlingService
import java.time.LocalDateTime
import java.util.UUID

class BestillingAvvistFraHotsak(
    rapidsConnection: RapidsConnection,
    private val søknadsbehandlingService: SøknadsbehandlingService,
) : AsyncPacketListener {
    init {
        River(rapidsConnection).apply {
            validate { it.demandValue("eventName", "hm-BestillingAvvist") }
            validate { it.requireKey("søknadId", "fodselsnummer", "opprettet", "valgte_arsaker", "begrunnelse") }
        }.register(this)
    }

    private val JsonMessage.søknadId get() = uuidValue("søknadId")
    private val JsonMessage.fnrBruker get() = this["fodselsnummer"].textValue()
    private val JsonMessage.opprettet get() = this["opprettet"].asLocalDateTime()
    private val JsonMessage.valgteÅrsaker get() = this["valgte_arsaker"].asObject<Set<String>>()
    private val JsonMessage.begrunnelse get() = this["begrunnelse"].textValue()

    override suspend fun onPacketAsync(packet: JsonMessage, context: MessageContext) {
        val søknadId = packet.søknadId
        val fnrBruker = packet.fnrBruker
        val valgteÅrsaker = packet.valgteÅrsaker
        val begrunnelse = packet.begrunnelse
        val opprettet = packet.opprettet

        søknadsbehandlingService.oppdaterStatus(
            søknadId,
            Statusendring(BehovsmeldingStatus.BESTILLING_AVVIST, valgteÅrsaker, begrunnelse)
        )

        context.publish(
            fnrBruker,
            BestillingAvvistLagretMelding(
                søknadId = søknadId,
                fnrBruker = fnrBruker,
                valgteÅrsaker = valgteÅrsaker,
                begrunnelse = begrunnelse,
                opprettet = opprettet,
            ),
        )
    }
}

data class BestillingAvvistLagretMelding(
    override val søknadId: SøknadId,
    val fnrBruker: String,
    val valgteÅrsaker: Set<String>,
    val begrunnelse: String,
    val opprettet: LocalDateTime,
) : TilknyttetSøknad, Melding {
    override val eventId: UUID = UUID.randomUUID()
    override val eventName: String = "hm-BestillingAvvistFraHotsakLagret"
}
