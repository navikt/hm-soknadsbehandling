package no.nav.hjelpemidler.soknad.mottak.river

import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.hjelpemidler.soknad.mottak.client.SøknadForRiverClient
import no.nav.hjelpemidler.soknad.mottak.metrics.Metrics
import no.nav.hjelpemidler.soknad.mottak.metrics.Prometheus
import no.nav.hjelpemidler.soknad.mottak.service.BehovsmeldingType
import no.nav.hjelpemidler.soknad.mottak.service.Signatur
import no.nav.hjelpemidler.soknad.mottak.service.SoknadData
import no.nav.hjelpemidler.soknad.mottak.service.Status
import no.nav.hjelpemidler.soknad.mottak.util.AutoGenerateDocumentTitle
import java.util.UUID

private val logger = KotlinLogging.logger {}
private val sikkerlogg = KotlinLogging.logger("tjenestekall")

/**
 * Plukker opp behovsmeldinger som er sendt inn av formidler og hvor det ikke er videre behov for bekreftelse fra bruker.
 * For søknader og bestillinger vil dette være pga at formidler har svart at bruker har signert fullmakt på papir.
 * For bytter er det ikke behov for bekreftelse fra bruker.
 */
internal class BehovsmeldingIkkeBehovForBrukerbekreftelseDataSink(
    rapidsConnection: RapidsConnection,
    private val søknadForRiverClient: SøknadForRiverClient,
    private val metrics: Metrics
) : PacketListenerWithOnError {

    init {
        River(rapidsConnection).apply {
            validate { it.demandValue("eventName", "nySoknad") }
            validate {
                it.demandAny(
                    "signatur",
                    listOf(
                        Signatur.FULLMAKT.name,
                        Signatur.FRITAK_FRA_FULLMAKT.name,
                        Signatur.IKKE_INNHENTET_FORDI_BYTTE.name,
                        Signatur.IKKE_INNHENTET_FORDI_BRUKERPASSBYTTE.name,
                    )
                )
            }
            validate { it.requireKey("fodselNrBruker", "fodselNrInnsender", "soknad", "eventId") }
            validate { it.forbid("soknadId", "kommuneNavn") }
        }.register(this)
    }

    private val JsonMessage.eventId get() = this["eventId"].textValue()
    private val JsonMessage.fnrBruker get() = this["fodselNrBruker"].textValue()
    private val JsonMessage.fnrInnsender get() = this["fodselNrInnsender"].textValue()
    private val JsonMessage.behovsmeldingId get() = this["soknad"]["id"].textValue()
    private val JsonMessage.behovsmeldingType get() = this["soknad"]["behovsmeldingType"].textValue()
    private val JsonMessage.behovsmelding get() = this["soknad"]
    private val JsonMessage.signatur get() = Signatur.valueOf(this["signatur"].textValue())

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        runBlocking {
            if (skipEvent(UUID.fromString(packet.eventId))) {
                logger.info { "Hopper over event i skip-list: ${packet.eventId}" }
                return@runBlocking
            }
            try {

                val navnBruker = when (packet.behovsmeldingType) {
                    BehovsmeldingType.BRUKERPASSBYTTE.name -> packet.behovsmelding["brukerpassbytte"]["brukersNavn"].textValue()
                    else -> packet.behovsmelding["soknad"]["bruker"]["fornavn"].textValue() + " " + packet.behovsmelding["soknad"]["bruker"]["etternavn"].textValue()
                }

                val soknadData = SoknadData(
                    fnrBruker = packet.fnrBruker,
                    navnBruker = navnBruker,
                    fnrInnsender = packet.fnrInnsender,
                    soknad = packet.behovsmelding,
                    soknadId = UUID.fromString(packet.behovsmeldingId),
                    status = packet.signatur.tilStatus(),
                    kommunenavn = null,
                    soknadGjelder = AutoGenerateDocumentTitle.generateTitle(packet.behovsmelding),
                )

                logger.info { "Behovsmelding med fullmakt eller uten behov for signatur mottatt: ${packet.behovsmeldingId}" }
                save(soknadData)

                forward(soknadData, context)

                metrics.digitalSoknad(packet.fnrBruker, packet.behovsmeldingId)
            } catch (e: Exception) {
                throw RuntimeException("Håndtering av event ${packet.eventId}, id ${packet.behovsmeldingId} feilet", e)
            }
        }
    }

    private fun skipEvent(eventId: UUID): Boolean {
        val skipList = mutableListOf<UUID>()
        skipList.add(UUID.fromString("01fc4654-bba8-43b3-807b-8487ab21cea3"))
        return skipList.any { it == eventId }
    }

    private suspend fun save(soknadData: SoknadData) =
        kotlin.runCatching {
            søknadForRiverClient.save(soknadData)
        }.onSuccess {
            logger.info("Behovsmelding saved: ${soknadData.soknadId}")
        }.onFailure {
            logger.error(it) { "Failed to save behovsmelding: ${soknadData.soknadId}" }
        }.getOrThrow()

    private fun forward(søknadData: SoknadData, context: MessageContext) {
        try {
            context.publish(søknadData.fnrBruker, søknadData.toJson("hm-behovsmeldingMottatt"))
            Prometheus.soknadMedFullmaktCounter.inc()
            logger.info { "Behovsmelding sent: ${søknadData.soknadId}" }
            sikkerlogg.info("Behovsmelding sendt med id: ${søknadData.soknadId}, fnr: ${søknadData.fnrBruker})")
        } catch (e: Exception) {
            logger.error(e) { "forward() failed: ${e.message}. Behovsmelding: ${søknadData.soknadId}" }
        }
    }
}

private fun Signatur.tilStatus() = when (this) {
    Signatur.IKKE_INNHENTET_FORDI_BYTTE -> Status.INNSENDT_FULLMAKT_IKKE_PÅKREVD
    Signatur.IKKE_INNHENTET_FORDI_BRUKERPASSBYTTE -> Status.BRUKERPASSBYTTE_INNSENDT
    else -> Status.GODKJENT_MED_FULLMAKT
}