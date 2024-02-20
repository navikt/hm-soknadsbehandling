package no.nav.hjelpemidler.soknad.mottak.river

import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.hjelpemidler.soknad.mottak.client.SøknadForRiverClient
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

internal class OppgaveSinkTest {
    private val soknadId = "2de7a34f-424a-4f2e-8e09-dfe55fc4aebd"
    private val oppgaveId = "57983"
    private val capturedOppgaveId = slot<String>()
    private val capturedSoknadId = slot<UUID>()
    private val mock = mockk<SøknadForRiverClient>().apply {
        coEvery { oppdaterOppgaveId(capture(capturedSoknadId), capture(capturedOppgaveId)) } returns 1
    }

    private val rapid = TestRapid().apply {
        OppgaveSink(this, mock)
    }

    @BeforeEach
    fun reset() {
        rapid.reset()
        capturedSoknadId.clear()
        capturedOppgaveId.clear()
    }

    @Test
    fun `Oppdater søknad med journalpostId`() {

        val oppgavePacket = """
            {
              "soknadId": "$soknadId",
              "eventName": "hm-OppgaveOpprettet",
              "opprettet": "2021-03-18T15:03:29.713372",
              "fnrBruker": "15084300133",
              "oppgaveId": "$oppgaveId"
            }
        """.trimMargin()

        rapid.sendTestMessage(oppgavePacket)

        capturedSoknadId.captured.toString() shouldBe soknadId
        capturedOppgaveId.captured shouldBe oppgaveId
    }
}
