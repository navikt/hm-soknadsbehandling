package no.nav.hjelpemidler.soknad.mottak.river

import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.hjelpemidler.soknad.mottak.client.SøknadsbehandlingClient
import no.nav.hjelpemidler.soknad.mottak.test.Json
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

internal class OppgaveSinkTest {
    private val søknadId = "2de7a34f-424a-4f2e-8e09-dfe55fc4aebd"
    private val capturedSøknadId = slot<UUID>()
    private val oppgaveId = "57983"
    private val capturedOppgaveId = slot<String>()
    private val mock = mockk<SøknadsbehandlingClient>().apply {
        coEvery { oppdaterOppgaveId(capture(capturedSøknadId), capture(capturedOppgaveId)) } returns 1
    }

    private val rapid = TestRapid().apply {
        OppgaveSink(this, mock)
    }

    @BeforeEach
    fun reset() {
        rapid.reset()
        capturedSøknadId.clear()
        capturedOppgaveId.clear()
    }

    @Test
    fun `Oppdater søknad med journalpostId`() {
        val oppgavePacket = Json(
            """
                {
                  "soknadId": "$søknadId",
                  "eventName": "hm-OppgaveOpprettet",
                  "opprettet": "2021-03-18T15:03:29.713372",
                  "fnrBruker": "15084300133",
                  "oppgaveId": "$oppgaveId"
                }
            """.trimIndent()
        )

        rapid.sendTestMessage(oppgavePacket.toString())

        capturedSøknadId.captured.toString() shouldBe søknadId
        capturedOppgaveId.captured shouldBe oppgaveId
    }
}
