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
import no.nav.hjelpemidler.domain.person.Personnavn
import no.nav.hjelpemidler.logging.teamInfo
import no.nav.hjelpemidler.serialization.jackson.jsonMapper
import no.nav.hjelpemidler.soknad.mottak.melding.BehovsmeldingMottattMelding
import no.nav.hjelpemidler.soknad.mottak.metrics.Metrics
import no.nav.hjelpemidler.soknad.mottak.metrics.Prometheus
import no.nav.hjelpemidler.soknad.mottak.soknadsbehandling.SøknadsbehandlingService
import java.util.UUID

private val log = KotlinLogging.logger {}

/**
 * Plukker opp behovsmeldinger som er sendt inn av formidler og hvor det ikke er videre behov for bekreftelse fra bruker,
 * og bytter sendt inn fra brukere med brukerpassrolle.
 * For søknader og bestillinger vil dette være pga. at formidler har svart at bruker har signert fullmakt på papir.
 * For bytter er det ikke behov for bekreftelse fra bruker.
 * For saker med kun enkeltstående tilbehør er det ikke behov for bekreftelse fra bruker.
 */
class BehovsmeldingIkkeBehovForBrukerbekreftelseDataSink(
    rapidsConnection: RapidsConnection,
    private val søknadsbehandlingService: SøknadsbehandlingService,
    private val metrics: Metrics,
) : AsyncPacketListener {
    init {
        River(rapidsConnection).apply {
            precondition {
                it.requireValue("eventName", "nySoknad")
                it.requireAny(
                    "signatur",
                    listOf(
                        Signaturtype.FULLMAKT,
                        Signaturtype.FRITAK_FRA_FULLMAKT,
                        Signaturtype.IKKE_INNHENTET_FORDI_BYTTE,
                        Signaturtype.IKKE_INNHENTET_FORDI_BRUKERPASSBYTTE,
                        Signaturtype.IKKE_INNHENTET_FORDI_KUN_TILBEHØR,
                        Signaturtype.IKKE_INNHENTET_FORDI_KUN_TILBEHØR_V2,
                        Signaturtype.IKKE_INNHENTET_FORDI_KUN_TILBEHØR_V3,
                    ).map(Signaturtype::name)
                )
            }
            validate {
                it.requireKey("fodselNrBruker", "fodselNrInnsender", "soknad", "eventId", "behovsmelding")
                it.forbid("soknadId")
            }
        }.register(this)
    }

    private val JsonMessage.eventId get() = uuidValue("eventId")
    private val JsonMessage.fnrBruker get() = this["fodselNrBruker"].textValue()
    private val JsonMessage.fnrInnsender get() = this["fodselNrInnsender"].textValue()
    private val JsonMessage.behovsmeldingV1 get() = this["soknad"]
    private val JsonMessage.behovsmeldingV2 get() = this["behovsmelding"]
    private val JsonMessage.behovsmeldingId get() = this.behovsmeldingV2["id"].uuidValue()
    private val JsonMessage.behovsmeldingType get() = BehovsmeldingType.valueOf(this.behovsmeldingV2["type"].textValue())
    private val JsonMessage.signatur get() = Signaturtype.valueOf(this["signatur"].textValue())

    override suspend fun onPacketAsync(packet: JsonMessage, context: MessageContext) {
        if (packet.eventId in skipList) {
            log.info { "Hopper over event i skipList: ${packet.eventId}" }
            return
        }

        val behovsmeldingId = packet.behovsmeldingId
        val fnrBruker = packet.fnrBruker

        try {
            val navnBruker = when (packet.behovsmeldingType) {
                BehovsmeldingType.BRUKERPASSBYTTE -> packet.behovsmeldingV2["navn"]
                else -> packet.behovsmeldingV2["bruker"]["navn"]
            }.let { jsonMapper.convertValue<Personnavn>(it) }.toString()

            val grunnlag = Behovsmeldingsgrunnlag.Digital(
                søknadId = behovsmeldingId,
                status = packet.signatur.tilStatus(),
                fnrBruker = fnrBruker,
                navnBruker = navnBruker,
                fnrInnsender = packet.fnrInnsender,
                behovsmelding = jsonMapper.convertValue(packet.behovsmeldingV1),
                behovsmeldingGjelder = AutoGenerateDocumentTitle.generateTitle(packet.behovsmeldingV1),
                behovsmeldingV2 = jsonMapper.convertValue(packet.behovsmeldingV2)
            )

            log.info { "Behovsmelding med fullmakt eller uten behov for signatur mottatt, søknadId: $behovsmeldingId (gjelder: '${grunnlag.behovsmeldingGjelder}')" }

            søknadsbehandlingService.lagreBehovsmelding(grunnlag)

            context.publish(fnrBruker, BehovsmeldingMottattMelding("hm-behovsmeldingMottatt", grunnlag, packet.behovsmeldingType))
            Prometheus.søknadMedFullmaktCounter.increment()
            log.info { "Behovsmelding sendt, søknadId: $behovsmeldingId" }
            log.teamInfo { "Behovsmelding sendt, søknadId: $behovsmeldingId, fnrBruker: $fnrBruker" }
            metrics.digitalSøknad(fnrBruker, behovsmeldingId)
        } catch (e: Exception) {
            log.error(e) { "Håndtering av eventId: ${packet.eventId}, søknadId: $behovsmeldingId feilet" }
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
