package no.nav.hjelpemidler.soknad.mottak.river

import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.Called
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.hjelpemidler.behovsmeldingsmodell.BehovsmeldingStatus
import no.nav.hjelpemidler.soknad.mottak.client.SøknadForRiverClient
import no.nav.hjelpemidler.soknad.mottak.test.lagSøknad
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

class GodkjennSoknadTest {
    private val søknadId = UUID.randomUUID()
    private val søknadIdDuplikat = UUID.randomUUID()
    private val capturedStatus = slot<BehovsmeldingStatus>()
    private val capturedSøknadId = slot<UUID>()
    private val mock = mockk<SøknadForRiverClient>().apply {
        coEvery { oppdaterStatus(capture(capturedSøknadId), capture(capturedStatus)) } returns 1
        coEvery { hentSøknad(søknadId, true) } returns lagSøknad(
            søknadId = søknadId,
            status = BehovsmeldingStatus.VENTER_GODKJENNING,
            data = """
                {
                  "fnrBruker": "fnrBruker",
                  "soknadId": "$søknadId",
                  "datoOpprettet": "2021-02-23T09:46:45.146+00:00",
                  "soknad": {
                    "id": "$søknadId",
                    "date": "2021-02-16",
                    "bruker": {
                      "kilde": "PDL",
                      "adresse": "Trandemveien 29",
                      "fnummer": "12345678910",
                      "fornavn": "Sedat",
                      "poststed": "Hebnes",
                      "signatur": "BRUKER_BEKREFTER",
                      "etternavn": "Kronjuvel",
                      "postnummer": "4235",
                      "telefonNummer": "12341234"
                    },
                    "levering": "postkassa, postkassa, postkassa",
                    "hjelpemidler": "foo",
                    "brukersituasjon": "bar"
                  }
                }
            """.trimIndent()
        )
        coEvery { hentSøknad(søknadIdDuplikat, true) } returns lagSøknad(
            søknadId = søknadIdDuplikat,
            status = BehovsmeldingStatus.GODKJENT,
            data = """
                {
                  "fnrBruker": "fnrBruker",
                  "soknadId": "$søknadIdDuplikat",
                  "datoOpprettet": "2021-02-23T09:46:45.146+00:00",
                  "soknad": {
                    "id": "$søknadIdDuplikat",
                    "date": "2021-02-16",
                    "bruker": {
                      "kilde": "PDL",
                      "adresse": "Trandemveien 29",
                      "fnummer": "12345678910",
                      "fornavn": "Sedat",
                      "poststed": "Hebnes",
                      "signatur": "BRUKER_BEKREFTER",
                      "etternavn": "Kronjuvel",
                      "postnummer": "4235",
                      "telefonNummer": "12341234"
                    },
                    "levering": "postkassa, postkassa, postkassa",
                    "hjelpemidler": "foo",
                    "brukersituasjon": "bar"
                  }
                }
            """.trimIndent()
        )
    }

    private val rapid = TestRapid().apply {
        GodkjennSoknad(this, mock)
    }

    @BeforeEach
    fun reset() {
        rapid.reset()
        capturedSøknadId.clear()
        capturedStatus.clear()
    }

    @Test
    fun `Do not forward already godkjent søknad`() {
        @Language("JSON")
        val okPacket = """
            {
                "eventName": "godkjentAvBruker",
                "fodselNrBruker": "fnrBruker",
                "soknadId": "$søknadIdDuplikat"
            }
        """.trimIndent()

        rapid.sendTestMessage(okPacket)

        Thread.sleep(1000)

        val inspektør = rapid.inspektør

        inspektør.size shouldBeExactly 0
    }

    @Test
    fun `Update soknad and forward if packet contains required keys`() {
        @Language("JSON")
        val okPacket = """
            {
                "eventName": "godkjentAvBruker",
                "fodselNrBruker": "01987654321",
                "soknadId": "$søknadId"
            }
        """.trimIndent()

        rapid.sendTestMessage(okPacket)

        capturedSøknadId.captured shouldBe søknadId
        capturedStatus.captured shouldBe BehovsmeldingStatus.GODKJENT

        Thread.sleep(1000)

        val inspektør = rapid.inspektør

        inspektør.size shouldBeExactly 1

        inspektør.key(0) shouldBe "01987654321"
        val jsonNode = inspektør.message(0)

        jsonNode["soknadId"].textValue() shouldBe søknadId.toString()
        jsonNode["fnrBruker"].textValue() shouldBe "01987654321"
        jsonNode["eventName"].textValue() shouldBe "hm-søknadGodkjentAvBrukerMottatt"
        jsonNode["opprettet"].textValue() shouldNotBe null
        jsonNode["soknad"] shouldNotBe null
    }

    @Test
    fun `Do not react to events without soknadId key`() {
        @Language("JSON")
        val invalidPacket = """
            {
                "eventName": "godkjentAvBruker",
                "fodselNrBruker": "fnrBruker"
            }
        """.trimIndent()

        Assertions.assertThrows(RiverRequiredKeyMissingException::class.java) {
            rapid.sendTestMessage(invalidPacket)
        }
    }

    @Test
    fun `Do not react to events with irrelevant eventName`() {
        @Language("JSON")
        val invalidPacket = """
            {
                "eventName": "theseAreNotTheEventsYouAreLookingFor",
                "fodselNrBruker": "fnrBruker",
                "soknadId": "$søknadId"
            }
        """.trimIndent()

        rapid.sendTestMessage(invalidPacket)

        verify { mock wasNot Called }
    }
}
