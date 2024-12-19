package no.nav.hjelpemidler.soknad.mottak.river

import com.fasterxml.jackson.module.kotlin.convertValue
import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.hjelpemidler.behovsmeldingsmodell.BehovsmeldingStatus
import no.nav.hjelpemidler.behovsmeldingsmodell.BehovsmeldingType
import no.nav.hjelpemidler.behovsmeldingsmodell.Behovsmeldingsgrunnlag
import no.nav.hjelpemidler.behovsmeldingsmodell.Signaturtype
import no.nav.hjelpemidler.serialization.jackson.jsonMapper
import no.nav.hjelpemidler.soknad.mottak.logging.sikkerlogg
import no.nav.hjelpemidler.soknad.mottak.melding.BehovsmeldingMottattMelding
import no.nav.hjelpemidler.soknad.mottak.metrics.Metrics
import no.nav.hjelpemidler.soknad.mottak.metrics.Prometheus
import no.nav.hjelpemidler.soknad.mottak.soknadsbehandling.SøknadsbehandlingService
import java.util.UUID

private val logger = KotlinLogging.logger {}

/**
 * Plukker opp behovsmeldinger som er sendt inn av formidler og hvor det ikke er videre behov for bekreftelse fra bruker,
 * og bytter sendt inn fra brukere med brukerpassrolle.
 * For søknader og bestillinger vil dette være pga. at formidler har svart at bruker har signert fullmakt på papir.
 * For bytter er det ikke behov for bekreftelse fra bruker.
 */
class BehovsmeldingIkkeBehovForBrukerbekreftelseDataSink(
    rapidsConnection: RapidsConnection,
    private val søknadsbehandlingService: SøknadsbehandlingService,
    private val metrics: Metrics,
) : AsyncPacketListener {
    init {
        River(rapidsConnection).apply {
            precondition { it.requireValue("eventName", "nySoknad") }
            precondition {
                it.requireAny(
                    "signatur",
                    listOf(
                        Signaturtype.FULLMAKT,
                        Signaturtype.FRITAK_FRA_FULLMAKT,
                        Signaturtype.IKKE_INNHENTET_FORDI_BYTTE,
                        Signaturtype.IKKE_INNHENTET_FORDI_BRUKERPASSBYTTE,
                    ).map(Signaturtype::name)
                )
            }
            validate { it.requireKey("fodselNrBruker", "fodselNrInnsender", "soknad", "eventId") }
            validate { it.forbid("soknadId") }
            validate { it.interestedIn("behovsmelding") } // vil ikke eksistere for brukerpassbytte
        }.register(this)
    }

    private val JsonMessage.eventId get() = uuidValue("eventId")
    private val JsonMessage.fnrBruker get() = this["fodselNrBruker"].textValue()
    private val JsonMessage.fnrInnsender get() = this["fodselNrInnsender"].textValue()
    private val JsonMessage.søknadId
        get() = this["soknad"]["id"]?.uuidValue()
            ?: this["soknad"]["soknad"]["id"].uuidValue() // todo -> fjern fallback ["soknad"]["soknad"]["id"] når brukerpassbytte er lansert
    private val JsonMessage.behovsmeldingType get() = this["soknad"]["behovsmeldingType"].textValue()
    private val JsonMessage.behovsmelding get() = this["soknad"]
    private val JsonMessage.signatur get() = Signaturtype.valueOf(this["signatur"].textValue())
    private val JsonMessage.behovsmeldingV2 get() = this["behovsmelding"]

    override suspend fun onPacketAsync(packet: JsonMessage, context: MessageContext) {
        if (packet.eventId in skipList) {
            logger.info { "Hopper over event i skipList: ${packet.eventId}" }
            return
        }

        val søknadId = packet.søknadId
        val fnrBruker = packet.fnrBruker

        try {
            val navnBruker = when (packet.behovsmeldingType) {
                BehovsmeldingType.BRUKERPASSBYTTE.name -> packet.behovsmelding["brukerpassbytte"]["brukersNavn"].textValue()
                else -> packet.behovsmelding["soknad"]["bruker"]["fornavn"].textValue() + " " + packet.behovsmelding["soknad"]["bruker"]["etternavn"].textValue()
            }

            val grunnlag = Behovsmeldingsgrunnlag.Digital(
                søknadId = søknadId,
                status = packet.signatur.tilStatus(),
                fnrBruker = fnrBruker,
                navnBruker = navnBruker,
                fnrInnsender = packet.fnrInnsender,
                behovsmelding = jsonMapper.convertValue(packet.behovsmelding),
                behovsmeldingGjelder = AutoGenerateDocumentTitle.generateTitle(packet.behovsmelding),
                behovsmeldingV2 = when (packet.behovsmeldingType) {
                    BehovsmeldingType.BRUKERPASSBYTTE.name -> null
                    else -> if (packet.behovsmeldingV2.isNull) null else jsonMapper.convertValue(packet.behovsmeldingV2)
                }
            )

            logger.info { "Behovsmelding med fullmakt eller uten behov for signatur mottatt, søknadId: $søknadId (gjelder: '${grunnlag.behovsmeldingGjelder}')" }

            søknadsbehandlingService.lagreBehovsmelding(grunnlag)

            context.publish(fnrBruker, BehovsmeldingMottattMelding("hm-behovsmeldingMottatt", grunnlag))
            Prometheus.søknadMedFullmaktCounter.increment()
            logger.info { "Behovsmelding sendt, søknadId: $søknadId" }
            sikkerlogg.info { "Behovsmelding sendt, søknadId: $søknadId, fnrBruker: $fnrBruker" }
            metrics.digitalSøknad(fnrBruker, søknadId)
        } catch (e: Exception) {
            logger.error(e) { "Håndtering av eventId: ${packet.eventId}, søknadId: $søknadId feilet" }
            throw e
        }
    }

    private val skipList = listOf(
        "01fc4654-bba8-43b3-807b-8487ab21cea3",
    ).map(UUID::fromString)
}

private fun Signaturtype.tilStatus(): BehovsmeldingStatus = when (this) {
    Signaturtype.IKKE_INNHENTET_FORDI_BYTTE -> BehovsmeldingStatus.INNSENDT_FULLMAKT_IKKE_PÅKREVD
    Signaturtype.IKKE_INNHENTET_FORDI_BRUKERPASSBYTTE -> BehovsmeldingStatus.BRUKERPASSBYTTE_INNSENDT
    else -> BehovsmeldingStatus.GODKJENT_MED_FULLMAKT
}
