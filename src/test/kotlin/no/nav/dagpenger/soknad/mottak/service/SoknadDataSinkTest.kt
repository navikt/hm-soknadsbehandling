package no.nav.dagpenger.soknad.mottak.service

import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.Called
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import no.nav.dagpenger.soknad.mottak.db.SoknadStore
import no.nav.dagpenger.soknad.mottak.oppslag.PDLClient
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class SoknadDataSinkTest {
    private val capturedSoknadData = slot<SoknadData>()
    private val capturedMapping = slot<SoknadJournalpostMapping>()
    private val mock = mockk<SoknadStore>().apply {
        every { save(capture(capturedSoknadData)) } returns 1
        every { save(capture(capturedMapping)) } returns 1
    }

    private val pdlClientMock = mockk<PDLClient>().also {
        coEvery { it.getAktorId(any()) } returns "aktorId"
    }

    private val rapid = TestRapid().apply {
        SoknadDataSink(this, mock, pdlClientMock)
    }

    @BeforeEach
    fun reset() {
        rapid.reset()
        capturedSoknadData.clear()
    }

    @Test
    fun `Save soknad and mapping if packet contains required keys`() {

        val okPacket =
            """
            {
                "aktoerId": "fnr",
                "brukerBehandlingId": "bid",
                "journalpostId": "jpid"
            }
        """.trimMargin()

        rapid.sendTestMessage(okPacket)

        capturedSoknadData.captured.fnr shouldBe "fnr"
        capturedSoknadData.captured.søknadsId shouldBe "bid"

        capturedMapping.captured.journalpostId shouldBe "jpid"
        capturedMapping.captured.søknadsId shouldBe "bid"
    }

    @Test
    fun `Handle soknad if packet contains required keys`() {
        rapid.sendTestMessage("""{ "aktoerId": "fnr", "brukerBehandlingId": "id2", "journalpostId": "abc" }""".trimIndent())

        Thread.sleep(1000)

        val inspektør = rapid.inspektør

        inspektør.size shouldBeExactly 1

        inspektør.key(0) shouldBe "fnr"
        val jsonNode = inspektør.message(0)

        jsonNode["@id"].isNull shouldBe false
        jsonNode["fnr"].textValue() shouldBe "fnr"
        jsonNode["@event_name"].textValue() shouldBe "Søknad"
        jsonNode["@opprettet"].textValue() shouldNotBe null
        jsonNode["aktørId"].textValue() shouldBe "aktorId"
        jsonNode["søknadsId"].textValue() shouldBe "id2"
    }

    @Test
    fun `Does not handle packet with @id`() {

        val forbiddenPacket =
            """
            {
                "aktoerId": "fnr",
                "brukerBehandlingId": "bid",
                "@id": "id"
            }
        """.trimMargin()

        rapid.sendTestMessage(forbiddenPacket)
        verify { mock wasNot Called }
    }
}
