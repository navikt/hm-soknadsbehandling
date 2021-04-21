package no.nav.hjelpemidler.soknad.mottak.service

import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.hjelpemidler.soknad.mottak.client.SøknadForRiverClient
import no.nav.hjelpemidler.soknad.mottak.river.JournalpostSink
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

internal class JournalpostSinkTest {
    private val soknadId = "2de7a34f-424a-4f2e-8e09-dfe55fc4aebd"
    private val journalpostId = "123456789"
    private val capturedJournalpostId = slot<String>()
    private val capturedSoknadId = slot<UUID>()
    private val mock = mockk<SøknadForRiverClient>().apply {
        coEvery { oppdaterJournalpostId(capture(capturedSoknadId), capture(capturedJournalpostId)) } returns 1
    }

    private val rapid = TestRapid().apply {
        JournalpostSink(this, mock)
    }

    @BeforeEach
    fun reset() {
        rapid.reset()
        capturedSoknadId.clear()
        capturedJournalpostId.clear()
    }

    @Test
    fun `Oppdater søknad med journalpostId`() {

        val journalpostPacket =
            """
            {
              "soknadId": "$soknadId",
              "eventName": "hm-SøknadArkivert",
              "opprettet": "2021-03-18T15:03:28.382522",
              "fnrBruker": "12345678910",
              "joarkRef": "$journalpostId",
              "eventId": "aa720643-3dd9-45f3-8ca8-6ecb46d83987"
            }
        """.trimMargin()

        rapid.sendTestMessage(journalpostPacket)

        capturedSoknadId.captured.toString() shouldBe soknadId
        capturedJournalpostId.captured shouldBe journalpostId
    }
}
