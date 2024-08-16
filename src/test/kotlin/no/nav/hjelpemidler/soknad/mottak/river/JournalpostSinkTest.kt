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

class JournalpostSinkTest {
    private val søknadId = "2de7a34f-424a-4f2e-8e09-dfe55fc4aebd"
    private val capturedSøknadId = slot<UUID>()
    private val journalpostId = "123456789"
    private val capturedJournalpostId = slot<String>()
    private val mock = mockk<SøknadsbehandlingClient>().apply {
        coEvery { oppdaterJournalpostId(capture(capturedSøknadId), capture(capturedJournalpostId)) } returns 1
    }

    private val rapid = TestRapid().apply {
        JournalpostSink(this, mock)
    }

    @BeforeEach
    fun reset() {
        rapid.reset()
        capturedSøknadId.clear()
        capturedJournalpostId.clear()
    }

    @Test
    fun `Oppdater søknad med journalpostId`() {
        val journalpostPacket = Json(
            """
                {
                  "soknadId": "$søknadId",
                  "eventName": "hm-SøknadArkivert",
                  "opprettet": "2021-03-18T15:03:28.382522",
                  "fnrBruker": "12345678910",
                  "joarkRef": "$journalpostId",
                  "eventId": "aa720643-3dd9-45f3-8ca8-6ecb46d83987"
                }
            """.trimIndent()
        )

        rapid.sendTestMessage(journalpostPacket.toString())

        capturedSøknadId.captured.toString() shouldBe søknadId
        capturedJournalpostId.captured shouldBe journalpostId
    }
}
