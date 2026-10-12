package no.nav.hjelpemidler.soknad.mottak.river

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.hjelpemidler.behovsmeldingsmodell.BehovsmeldingStatus
import no.nav.hjelpemidler.behovsmeldingsmodell.Behovsmeldingsgrunnlag
import no.nav.hjelpemidler.behovsmeldingsmodell.sak.HotsakSakId
import no.nav.hjelpemidler.behovsmeldingsmodell.sak.Sakstilknytning
import no.nav.hjelpemidler.domain.person.Fødselsnummer
import no.nav.hjelpemidler.serialization.jackson.fødselsnummerValue
import no.nav.hjelpemidler.serialization.jackson.stringValueOrNull
import no.nav.hjelpemidler.serialization.jackson.uuidValueOrNull
import no.nav.hjelpemidler.soknad.mottak.soknadsbehandling.SøknadsbehandlingService
import java.util.UUID

private val log = KotlinLogging.logger {}

class JournalpostJournalført(
    rapidsConnection: RapidsConnection,
    private val søknadsbehandlingService: SøknadsbehandlingService,
) : AsyncPacketListener {
    init {
        River(rapidsConnection).apply {
            precondition { it.requireValue("eventName", "hm-journalpost-oppdatert-og-ferdigstilt") }
            validate {
                it.requireKey(
                    "eventId",
                    "journalpostId",
                    "sakId",
                    "fnrBruker",
                )
                it.interestedIn(
                    "søknadId",
                    "navnBruker",
                    "søknadGjelder",
                )
            }
        }.register(this)
    }

    private val JsonMessage.eventId: UUID get() = this["eventId"].uuidValue()
    private val JsonMessage.journalpostId: String get() = this["journalpostId"].stringValue()
    private val JsonMessage.søknadId: UUID? get() = this["søknadId"].uuidValueOrNull()
    private val JsonMessage.sakId: HotsakSakId get() = this["sakId"].stringValue().let(::HotsakSakId)
    private val JsonMessage.fnrBruker: Fødselsnummer get() = this["fnrBruker"].fødselsnummerValue()
    private val JsonMessage.navnBruker: String? get() = this["navnBruker"].stringValueOrNull()
    private val JsonMessage.søknadGjelder: String? get() = this["søknadGjelder"].stringValueOrNull()

    override suspend fun onPacketAsync(packet: JsonMessage, context: MessageContext) {
        val journalpostId = packet.journalpostId
        val søknadId = packet.søknadId ?: return
        val navnBruker = packet.navnBruker ?: return
        try {
            val status = BehovsmeldingStatus.ENDELIG_JOURNALFØRT
            val lagret = søknadsbehandlingService.lagreBehovsmelding(
                Behovsmeldingsgrunnlag.Journalpost(
                    søknadId = søknadId,
                    status = status,
                    fnrBruker = packet.fnrBruker.toString(),
                    navnBruker = navnBruker ?: "",
                    journalpostId = journalpostId,
                    sakstilknytning = Sakstilknytning.Hotsak(sakId = packet.sakId, status = status),
                    kanal = null, // fixme
                    søknadGjelder = packet.søknadGjelder,
                )
            )
            if (!lagret) {
                log.warn { "En søknad med dette fødselsnummeret og journalpostId: $journalpostId er allerede lagret, søknadId: $søknadId" }
                return
            }

            // todo -> Send melding til Ditt NAV
            // context.publish(fnrBruker, SøknadUnderBehandlingMelding(søknadId, fnrBruker, BehovsmeldingType.SØKNAD))

            // todo -> metrics
        } catch (e: Exception) {
            log.error(e) { "Håndtering av eventId: ${packet.eventId}, søknadId: $søknadId feilet" }
            throw e
        }
    }

    private val skipList = listOf<UUID>()
}

/*
INFO 2026-10-12T13:29:45.025197977Z [resource.labels.containerName: hm-soknadsbehandling] Validering av melding feilet: 'Message has errors: Errors: 2 Missing required key søknadId Missing required key navnBruker Original message: {"journalpostId":"454219656","nyJournalpostId":"454219656","hoveddokumentTittel":"Søknad om hjelpemidler","fnrBruker":"13820599335","sakId":"4410","sak":{"fagsakId":"4410","fagsaksystem":"HJELPEMIDLER","sakstype":"FAGSAK"},"journalførendeEnhet":"2970","journalførtAv":"Z994579","oppgaveId":"434907","oppgavegrunnlagId":"01a1209d-c7b5-7a7b-9977-0686c34237d5","opprettet":"2026-10-09T14:22:54.60579316","eventId":"364f6df6-98bf-4379-a151-462cc14b1278","eventName":"hm-journalpost-oppdatert-og-ferdigstilt","@forårsaket_av":{"id":"a844217e-d3a8-4395-bacb-ce213fb871d0","opprettet":"2026-10-09T14:22:54.000048894"},"@id":"1d84ac76-c537-4a63-adec-43104ddf37d4","@opprettet":"2026-10-09T14:22:54.606268770","system_participating_services":[{"id":"1d84ac76-c537-4a63-adec-43104ddf37d4","time":"2026-10-09T14:22:54.606268770","service":"hm-joark-sink","instance":"hm-joark-sink-5b7f75994-ldkk5","image":"europe-north1-docker.pkg.dev/nais-management-233d/teamdigihot/hm-joark-sink:2026.10.07-13.30-1120bce@sha256:16bde4447fd67b0cd1fc4e70551de60ec43f05a9b55021544611da69acea0d0e"}]} '
  {
    "insertId": "1fcd3gqf972bt1",
    "jsonPayload": {
      "@timestamp": "2026-10-12T15:29:44.93265852+02:00",
      "level_value": 20000,
      "rapids_record_produced_time": "1791548574608",
      "rapids_record_before_notify_time": "2026-10-12T15:29:44.827865842",
      "rapids_poll_count": "500",
      "rapids_record_topic": "teamdigihot.hm-soknadsbehandling-v1",
      "logger_name": "no.nav.hjelpemidler.soknad.mottak.river.AsyncPacketListener",
      "rapids_record_id": "14c36141-b153-48b4-8a35-eba9948a0331",
      "tags": [
        "TEAM_LOGS"
      ],
      "message": "Validering av melding feilet: 'Message has errors:\n\t\nErrors: 2\n\tMissing required key søknadId\n\tMissing required key navnBruker\nOriginal message: {\"journalpostId\":\"454219656\",\"nyJournalpostId\":\"454219656\",\"hoveddokumentTittel\":\"Søknad om hjelpemidler\",\"fnrBruker\":\"13820599335\",\"sakId\":\"4410\",\"sak\":{\"fagsakId\":\"4410\",\"fagsaksystem\":\"HJELPEMIDLER\",\"sakstype\":\"FAGSAK\"},\"journalførendeEnhet\":\"2970\",\"journalførtAv\":\"Z994579\",\"oppgaveId\":\"434907\",\"oppgavegrunnlagId\":\"01a1209d-c7b5-7a7b-9977-0686c34237d5\",\"opprettet\":\"2026-10-09T14:22:54.60579316\",\"eventId\":\"364f6df6-98bf-4379-a151-462cc14b1278\",\"eventName\":\"hm-journalpost-oppdatert-og-ferdigstilt\",\"@forårsaket_av\":{\"id\":\"a844217e-d3a8-4395-bacb-ce213fb871d0\",\"opprettet\":\"2026-10-09T14:22:54.000048894\"},\"@id\":\"1d84ac76-c537-4a63-adec-43104ddf37d4\",\"@opprettet\":\"2026-10-09T14:22:54.606268770\",\"system_participating_services\":[{\"id\":\"1d84ac76-c537-4a63-adec-43104ddf37d4\",\"time\":\"2026-10-09T14:22:54.606268770\",\"service\":\"hm-joark-sink\",\"instance\":\"hm-joark-sink-5b7f75994-ldkk5\",\"image\":\"europe-north1-docker.pkg.dev/nais-management-233d/teamdigihot/hm-joark-sink:2026.10.07-13.30-1120bce@sha256:16bde4447fd67b0cd1fc4e70551de60ec43f05a9b55021544611da69acea0d0e\"}]}\n'",
      "team_logs_client": "tcp://10.6.93.167:55228",
      "rapids_record_offset": "151152",
      "thread_name": "main",
      "rapids_record_produced_time_type": "CreateTime",
      "rapids_record_partition": "8",
      "@version": "1",
      "rapids_poll_time": "2026-10-12T15:29:44.827106992",
      "rapids_poll_id": "51bd1c71-fb45-4f2a-a210-2f7c825d0d04"
    },
    "resource": {
      "type": "k8s_container",
      "labels": {
        "cluster_name": "dev",
        "pod_name": "hm-soknadsbehandling-9745cd59-s7n7t",
        "namespace_name": "teamdigihot",
        "project_id": "nais-dev-2e7b",
        "location": "gcp",
        "container_name": "hm-soknadsbehandling"
      }
    },
    "timestamp": "2026-10-12T13:29:45.025197977Z",
    "severity": "INFO",
    "logName": "projects/teamdigihot-dev-9705/logs/k8s_container.teamdigihot.hm-soknadsbehandling-9745cd59-s7n7t.hm-soknadsbehandling",
    "receiveTimestamp": "2026-10-12T13:29:45.108398855Z"
  }

 */
