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

/**
 * Denne hendelsen oppstår i `hm-joark-sink` etter at manuell journalføring av inngående journalpost er gjennomført i Hotsak.
 */
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
                    "hoveddokumentTittel",
                    "søknadId",
                    "navnBruker",
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
    private val JsonMessage.hoveddokumentTittel: String? get() = this["hoveddokumentTittel"].stringValueOrNull()

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
                    navnBruker = navnBruker,
                    journalpostId = journalpostId,
                    sakstilknytning = Sakstilknytning.Hotsak(sakId = packet.sakId),
                    kanal = null, // fixme
                    søknadGjelder = packet.hoveddokumentTittel,
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
