package no.nav.hjelpemidler.soknad.mottak.river

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helse.rapids_rivers.asLocalDateTime
import no.nav.hjelpemidler.behovsmeldingsmodell.BehovsmeldingStatus
import no.nav.hjelpemidler.soknad.mottak.asObject
import no.nav.hjelpemidler.soknad.mottak.service.BestillingAvvistLagretData
import no.nav.hjelpemidler.soknad.mottak.soknadsbehandling.Statusendring
import no.nav.hjelpemidler.soknad.mottak.soknadsbehandling.SøknadsbehandlingService

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
        val opprettet = packet.opprettet
        val valgteÅrsaker = packet.valgteÅrsaker
        val begrunnelse = packet.begrunnelse

        søknadsbehandlingService.oppdaterStatus(
            søknadId,
            Statusendring(BehovsmeldingStatus.BESTILLING_AVVIST, valgteÅrsaker, begrunnelse)
        )

        val bestillingAvvistLagretData = BestillingAvvistLagretData(
            søknadId,
            fnrBruker,
            opprettet,
            begrunnelse,
            valgteÅrsaker.toList(),
        )

        context.publish(fnrBruker, bestillingAvvistLagretData.toJson("hm-BestillingAvvistFraHotsakLagret"))
    }
}
