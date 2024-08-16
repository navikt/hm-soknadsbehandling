package no.nav.hjelpemidler.soknad.mottak.river

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helse.rapids_rivers.asLocalDateTime
import no.nav.hjelpemidler.behovsmeldingsmodell.BehovsmeldingStatus
import no.nav.hjelpemidler.behovsmeldingsmodell.SøknadId
import no.nav.hjelpemidler.behovsmeldingsmodell.TilknyttetSøknad
import no.nav.hjelpemidler.soknad.mottak.melding.Melding
import no.nav.hjelpemidler.soknad.mottak.soknadsbehandling.SøknadsbehandlingService
import java.time.LocalDateTime
import java.util.UUID

class BestillingFerdigstiltFraHotsak(
    rapidsConnection: RapidsConnection,
    private val søknadsbehandlingService: SøknadsbehandlingService,
) : AsyncPacketListener {
    init {
        River(rapidsConnection).apply {
            validate { it.demandValue("eventName", "hm-BestillingFerdigstilt") }
            validate { it.requireKey("søknadId", "fodselsnummer", "opprettet") }
        }.register(this)
    }

    private val JsonMessage.søknadId get() = uuidValue("søknadId")
    private val JsonMessage.fnrBruker get() = this["fodselsnummer"].textValue()
    private val JsonMessage.opprettet get() = this["opprettet"].asLocalDateTime()

    override suspend fun onPacketAsync(packet: JsonMessage, context: MessageContext) {
        val søknadId = packet.søknadId
        val fnrBruker = packet.fnrBruker
        val opprettet = packet.opprettet

        søknadsbehandlingService.oppdaterStatus(søknadId, BehovsmeldingStatus.BESTILLING_FERDIGSTILT)

        context.publish(fnrBruker, BestillingGodkjentLagretMelding(søknadId, fnrBruker, opprettet))
    }
}

data class BestillingGodkjentLagretMelding(
    override val søknadId: SøknadId,
    val fnrBruker: String,
    val opprettet: LocalDateTime,
) : TilknyttetSøknad, Melding {
    override val eventId: UUID = UUID.randomUUID()
    override val eventName: String = "hm-BestillingGodkjentFraHotsakLagret"
}
