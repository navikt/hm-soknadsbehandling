package no.nav.hjelpemidler.soknad.mottak.river

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.hjelpemidler.behovsmeldingsmodell.BehovsmeldingStatus
import no.nav.hjelpemidler.soknad.mottak.melding.BehovsmeldingMottattMelding
import no.nav.hjelpemidler.soknad.mottak.soknadapi.SøknadApiService
import no.nav.hjelpemidler.soknad.mottak.soknadsbehandling.SøknadsbehandlingService
import java.util.UUID

private val log = KotlinLogging.logger {}

/**
 * Når en behovsmelding endres fra brukerbekreftelse til fullmakt, vil den først få oppdatert datamodell i hm-soknadsbehandling-db,
 * og status settes til FULLMAKT_AVVENTER_PDF. Denne listeneren fanger opp dette og sørger deretter for at en ny PDF genereres
 * og at hm-soknadsbehandling-db deretter endrer statusen vider til GODKJENT_MED_FULLMAKT. Vi kan deretter fortsette saksbehandlingen
 * som vanlig.
 */

private const val eventName = "hm-brukerbekreftelse-til-fullmakt-avventer-pdf"

class BehovsmeldingAvventerPdfDataSink(
    rapidsConnection: RapidsConnection,
    private val søknadsbehandlingService: SøknadsbehandlingService,
    private val søknadApiService: SøknadApiService,
) : AsyncPacketListener {
    init {
        River(rapidsConnection).apply {
            precondition { it.requireValue("eventName", eventName) }
            validate { it.requireKey("behovsmeldingId", "eventId") }
        }.register(this)
    }

    private val JsonMessage.eventId get() = uuidValue("eventId")
    private val JsonMessage.behovsmeldingId get() = uuidValue("behovsmeldingId")

    override suspend fun onPacketAsync(packet: JsonMessage, context: MessageContext) {
        if (packet.eventId in skipList) {
            log.info { "Hopper over event i skipList: ${packet.eventId}" }
            return
        }

        val behovsmeldingId = packet.behovsmeldingId

        try {
            log.info { "Mottok event $eventName for behovsmeldingId $behovsmeldingId" }
            val (_, innsenderbehovsmelding, fnrInnsender, behovsmeldingGjelder) = søknadsbehandlingService.hentBehovsmeldingMedMetadata(
                behovsmeldingId
            )

            søknadApiService.genererNyPdf(behovsmeldingId, innsenderbehovsmelding)

            søknadsbehandlingService.oppdaterStatus(behovsmeldingId, BehovsmeldingStatus.GODKJENT_MED_FULLMAKT)

            context.publish(
                behovsmeldingId.toString(),
                BehovsmeldingMottattMelding(
                    eventName = "hm-behovsmeldingMottatt",
                    søknadId = behovsmeldingId,
                    fnrBruker = innsenderbehovsmelding.hjmBrukersFnr.value,
                    fnrInnsender = fnrInnsender.value,
                    behovsmeldingGjelder = behovsmeldingGjelder,
                    behovsmeldingType = innsenderbehovsmelding.type
                )
            )
            log.info { "Behovsmelding endret til fullmakt og videresendt til saksfordeling, søknadId: $behovsmeldingId" }
        } catch (e: Exception) {
            log.error(e) { "Håndtering av event $eventName feilet. eventId: ${packet.eventId}, behovsmeldingId: $behovsmeldingId" }
            throw e
        }
    }

    private val skipList = listOf<String>().map(UUID::fromString)
}

