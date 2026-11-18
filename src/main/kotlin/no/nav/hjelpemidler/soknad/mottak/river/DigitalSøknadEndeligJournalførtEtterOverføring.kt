package no.nav.hjelpemidler.soknad.mottak.river

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.hjelpemidler.behovsmeldingsmodell.BehovsmeldingStatus
import no.nav.hjelpemidler.behovsmeldingsmodell.sak.InfotrygdSakId
import no.nav.hjelpemidler.behovsmeldingsmodell.sak.Sakstilknytning
import no.nav.hjelpemidler.configuration.Environment
import no.nav.hjelpemidler.http.slack.SlackClient
import no.nav.hjelpemidler.http.slack.slackIconEmoji
import no.nav.hjelpemidler.soknad.mottak.melding.OvervåkVedtaksresultatMelding
import no.nav.hjelpemidler.soknad.mottak.metrics.Prometheus
import no.nav.hjelpemidler.soknad.mottak.soknadsbehandling.SøknadsbehandlingService
import java.util.UUID

private val log = KotlinLogging.logger {}

/**
 * Journalpost for søknad har blitt endelig journalført i Gosys etter overføring fra Hotsak til Gosys.
 */
class DigitalSøknadEndeligJournalførtEtterOverføring(
    rapidsConnection: RapidsConnection,
    private val søknadsbehandlingService: SøknadsbehandlingService,
    private val slackClient: SlackClient,
) : AsyncPacketListener {
    init {
        River(rapidsConnection).apply {
            precondition { it.requireValue("eventName", "DigitalSoeknadEndeligJournalfoertEtterTilbakefoering") }
            validate {
                it.requireKey(
                    "soknadId",
                    "fodselNrBruker",
                    "hendelse",
                    "hendelse.journalingEventSAF",
                    "hendelse.journalingEventSAF.sak",
                    "hendelse.journalingEventSAF.sak.fagsakId",
                    "hendelse.journalingEventSAF.journalpostId",
                )
            }
        }.register(this)
    }

    private val JsonMessage.søknadId get() = uuidValue("soknadId")
    private val JsonMessage.fnrBruker get() = this["fodselNrBruker"].stringValue()
    private val JsonMessage.fagsakId get() = InfotrygdSakId(this["hendelse"]["journalingEventSAF"]["sak"]["fagsakId"].stringValue())
    private val JsonMessage.journalpostId get() = this["hendelse"]["journalingEventSAF"]["journalpostId"].stringValue()

    override suspend fun onPacketAsync(packet: JsonMessage, context: MessageContext) {
        val søknadId = packet.søknadId
        val fnrBruker = packet.fnrBruker
        val fagsakId = packet.fagsakId
        val journalpostId = packet.journalpostId

        val søknad = søknadsbehandlingService.finnSøknad(søknadId)
        if (søknad == null) {
            log.warn { "Fant ikke søknad ved endelig journalføring etter overføring, søknadId: $søknadId" }
        } else if (søknad.fnrBruker != fnrBruker) {
            log.warn {
                "Bruker endret ved journalføring i Gosys etter overføring fra Hotsak, søknadId: $søknadId, journalpostId: $journalpostId"
            }
            Prometheus.søknadBrukerEndretEtterOverføringCounter.increment()
            varsleSlack(søknadId, journalpostId)
        }

        // fixme -> burde ikke de neste to operasjonene skje i samme transaksjon i backend?
        søknadsbehandlingService.oppdaterStatus(søknadId, BehovsmeldingStatus.ENDELIG_JOURNALFØRT)
        søknadsbehandlingService.lagreSakstilknytning(søknadId, Sakstilknytning.Infotrygd(fagsakId, fnrBruker))

        // På dette tidspunktet har det ikkje blitt gjort eit vedtak i Infotrygd, så vedtaksresultat og vedtaksdato er null
        context.publish(fnrBruker, OvervåkVedtaksresultatMelding(søknadId, fnrBruker, fagsakId))
        log.info { "Endelig journalført digital søknad mottatt, den er lagret og det er gitt beskjed til hm-infotrygd-poller og hm-ditt-nav for søknadId: $søknadId" }
    }

    private suspend fun varsleSlack(søknadId: UUID, journalpostId: String) {
        try {
            slackClient.sendMessage(
                username = "hm-soknadsbehandling",
                icon = slackIconEmoji(":warning:"),
                channel = "#digihot-alerts",
                message = ":warning: [${Environment.current}] Bruker endret ved journalføring av digital behovsmelding i Gosys etter overføring fra Hotsak. søknadId=$søknadId, journalpostId=$journalpostId. Sjekk om søknaden fortsatt vises på gammel bruker sin hjelpemiddelside!",
            )
        } catch (e: Exception) {
            // Logger ikke exception: Ktor-feil inneholder request-URL, altså webhook-hemmeligheten.
            log.error { "Kunne ikke sende Slack-varsel om endret bruker, søknadId: $søknadId, feiltype: ${e::class.simpleName}" }
        }
    }
}
