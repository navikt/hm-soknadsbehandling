package no.nav.hjelpemidler.soknad.mottak.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import io.mockk.Called
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.hjelpemidler.soknad.mottak.db.SoknadStore
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class JournalPostSinkTest {

    private val mapper = ObjectMapper()
    private val journalPost = JournalPost(
        journalpostId = "jpId",
        aktørId = "aktorId",
        naturligIdent = "fnr",
        behandlendeEnhet = "enhet",
        datoRegistrert = null,
        henvendelsestype = "NY_SØKNAD",
        fagsakId = "fagsakId"
    )

    val mock = mockk<SoknadStore>().apply {
        every { save(journalPost) } returns 1
    }

    private val rapid = TestRapid().apply {
        JournalPostSink(this, mock)
    }

    @BeforeEach
    fun reset() {
        rapid.reset()
    }

    @Test
    fun `Save journal post if packet contains required keys`() {

        val okPacket = mapper.valueToTree<ObjectNode>(journalPost).apply {
            this.put("ferdigBehandlet", true)
        }.toString()

        rapid.sendTestMessage(okPacket)

        verify(exactly = 1) { mock.save(journalPost) }
    }

    @Test
    fun `Does not handle packet with @id`() {
        val forbiddenPacket = mapper.valueToTree<ObjectNode>(journalPost).apply {
            this.put("@id", "id")
        }.toString()

        rapid.sendTestMessage(forbiddenPacket)

        verify { mock wasNot Called }
    }
}
