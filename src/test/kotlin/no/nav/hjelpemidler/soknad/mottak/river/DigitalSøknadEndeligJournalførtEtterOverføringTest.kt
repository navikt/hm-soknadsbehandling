package no.nav.hjelpemidler.soknad.mottak.river

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import no.nav.hjelpemidler.behovsmeldingsmodell.BehovsmeldingStatus
import no.nav.hjelpemidler.http.slack.SlackClient
import no.nav.hjelpemidler.behovsmeldingsmodell.SøknadDto
import no.nav.hjelpemidler.soknad.mottak.soknadsbehandling.SøknadsbehandlingService
import no.nav.hjelpemidler.soknad.mottak.test.Json
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

class DigitalSøknadEndeligJournalførtEtterOverføringTest {
    private val søknadId = UUID.fromString("081b4dbf-602a-448d-97c7-3a0126fb8d3e")
    private val fnrVedInnsending = "26848497710"
    private val fnrVedJournalføring = "15084300133"
    private val søknadsbehandlingService = mockk<SøknadsbehandlingService>()
    private val slackClient = mockk<SlackClient>(relaxed = true)
    private val rapid = TestRapid().apply {
        DigitalSøknadEndeligJournalførtEtterOverføring(this, søknadsbehandlingService, slackClient)
    }

    @BeforeEach
    fun reset() {
        rapid.reset()
        coEvery { søknadsbehandlingService.oppdaterStatus(any<UUID>(), any<BehovsmeldingStatus>()) } returns true
        coEvery { søknadsbehandlingService.lagreSakstilknytning(any(), any()) } returns true
    }

    @Test
    fun `likt fnr lagrer sakstilknytning og publiserer`() {
        coEvery { søknadsbehandlingService.finnSøknad(søknadId) } returns søknad(fnrVedInnsending)

        rapid.sendTestMessage(eventJson(fnrVedInnsending))

        coVerify(exactly = 1) { søknadsbehandlingService.lagreSakstilknytning(søknadId, any()) }
        coVerify(exactly = 0) { slackClient.sendMessage(any(), any(), any(), any()) }
        rapid.inspektør.size shouldBe 1
    }

    @Test
    fun `ulikt fnr sender Slack-varsel og behandles som før`() {
        coEvery { søknadsbehandlingService.finnSøknad(søknadId) } returns søknad(fnrVedInnsending)
        val melding = slot<String>()
        coEvery { slackClient.sendMessage(any(), any(), any(), capture(melding)) } returns Unit

        rapid.sendTestMessage(eventJson(fnrVedJournalføring))

        coVerify(exactly = 1) {
            søknadsbehandlingService.oppdaterStatus(søknadId, BehovsmeldingStatus.ENDELIG_JOURNALFØRT)
        }
        coVerify(exactly = 1) { søknadsbehandlingService.lagreSakstilknytning(søknadId, any()) }
        coVerify(exactly = 1) { slackClient.sendMessage(any(), any(), any(), any()) }
        melding.captured shouldContain søknadId.toString()
        melding.captured shouldContain "453647364"
        melding.captured shouldNotContain fnrVedInnsending
        melding.captured shouldNotContain fnrVedJournalføring
        rapid.inspektør.size shouldBe 1
    }

    @Test
    fun `manglende søknad fortsetter med sakstilknytning og publisering`() {
        coEvery { søknadsbehandlingService.finnSøknad(søknadId) } returns null

        rapid.sendTestMessage(eventJson(fnrVedJournalføring))

        coVerify(exactly = 1) { søknadsbehandlingService.lagreSakstilknytning(søknadId, any()) }
        coVerify(exactly = 0) { slackClient.sendMessage(any(), any(), any(), any()) }
        rapid.inspektør.size shouldBe 1
    }

    @Test
    fun `slack feil hindrer ikke behandling av ulikt fnr`() {
        coEvery { søknadsbehandlingService.finnSøknad(søknadId) } returns søknad(fnrVedInnsending)
        coEvery { slackClient.sendMessage(any(), any(), any(), any()) } throws RuntimeException("test failure")

        shouldNotThrowAny {
            rapid.sendTestMessage(eventJson(fnrVedJournalføring))
        }

        coVerify(exactly = 1) {
            søknadsbehandlingService.oppdaterStatus(søknadId, BehovsmeldingStatus.ENDELIG_JOURNALFØRT)
        }
        coVerify(exactly = 1) { søknadsbehandlingService.lagreSakstilknytning(søknadId, any()) }
        rapid.inspektør.size shouldBe 1
    }

    private fun søknad(fnrBruker: String) = mockk<SøknadDto> {
        every { this@mockk.fnrBruker } returns fnrBruker
    }

    private fun eventJson(fnrBruker: String) = Json(
        """
        {
          "eventName": "DigitalSoeknadEndeligJournalfoertEtterTilbakefoering",
          "soknadId": "$søknadId",
          "fodselNrBruker": "$fnrBruker",
          "hendelse": {
            "journalingEventSAF": {
              "journalpostId": "453647364",
              "sak": {
                "fagsakId": "4203A05"
              }
            }
          }
        }
        """.trimIndent(),
    ).toString()
}
