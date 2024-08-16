package no.nav.hjelpemidler.soknad.mottak.river

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.hjelpemidler.behovsmeldingsmodell.BehovsmeldingStatus
import no.nav.hjelpemidler.soknad.mottak.melding.SøknadUnderBehandlingMelding
import no.nav.hjelpemidler.soknad.mottak.soknadsbehandling.SøknadsbehandlingService

private val log = KotlinLogging.logger {}

class DigitalSøknadAutomatiskJournalført(
    rapidsConnection: RapidsConnection,
    private val søknadsbehandlingService: SøknadsbehandlingService,
) : AsyncPacketListener {
    init {
        River(rapidsConnection).apply {
            validate { it.demandValue("eventName", "hm-opprettetOgFerdigstiltJournalpost") }
            validate { it.requireKey("soknadId", "sakId", "joarkRef", "fnrBruker") }
        }.register(this)
    }

    private val JsonMessage.søknadId get() = uuidValue("soknadId")
    private val JsonMessage.sakId get() = get("sakId").textValue()
    private val JsonMessage.journalpostId get() = get("joarkRef").textValue()
    private val JsonMessage.fnrBruker get() = get("fnrBruker").textValue()

    override suspend fun onPacketAsync(packet: JsonMessage, context: MessageContext) {
        val søknadId = packet.søknadId
        val sakId = packet.sakId
        val journalpostId = packet.journalpostId
        val fnrBruker = packet.fnrBruker

        log.info { "Digital søknad journalført, søknadId: $søknadId, sakId: $sakId, journalpostId: $journalpostId" }

        val oppdatert = søknadsbehandlingService.oppdaterStatus(søknadId, BehovsmeldingStatus.ENDELIG_JOURNALFØRT)
        if (oppdatert) {
            val behovsmeldingType = søknadsbehandlingService.hentBehovsmeldingstype(søknadId)

            log.info {
                "Status på ${
                    behovsmeldingType.toString().lowercase()
                } satt til endelig journalført, søknadId: $søknadId"
            }

            // Melding til Ditt NAV
            context.publish(fnrBruker, SøknadUnderBehandlingMelding(søknadId, fnrBruker, behovsmeldingType))
        } else {
            log.warn { "Status er allerede satt til endelig journalført, søknadId: $søknadId, sakId: $sakId, journalpostId: $journalpostId" }
        }
    }
}
