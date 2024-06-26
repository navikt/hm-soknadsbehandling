package no.nav.hjelpemidler.soknad.mottak.river

import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.Called
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.hjelpemidler.soknad.mottak.client.SøknadForRiverClient
import no.nav.hjelpemidler.soknad.mottak.service.Status
import no.nav.hjelpemidler.soknad.mottak.service.SøknadData
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

internal class GodkjennSoknadTest {
    private val soknadId = "e8dac11d-fa66-4561-89d7-88a62ab31c2b"
    private val soknadIdDuplikat = "b6babd58-e46e-4adf-9799-707d41c5322e"
    private val capturedStatus = slot<Status>()
    private val capturedSoknadId = slot<UUID>()
    private val mock = mockk<SøknadForRiverClient>().apply {
        coEvery { oppdaterStatus(capture(capturedSoknadId), capture(capturedStatus)) } returns 1
        coEvery { hentSøknadData(UUID.fromString(soknadId)) } returns SøknadData(
            "fnrBruker",
            "fornavn etternavn",
            "fnrInnsender",
            UUID.fromString(soknadId),
            ObjectMapper().readTree(
                """ {
                    "fnrBruker": "fnrBruker",
                    "soknadId": "$soknadId",
                    "datoOpprettet": "2021-02-23T09:46:45.146+00:00",
                    "soknad": {
                        "id": "e8dac11d-fa66-4561-89d7-88a62ab31c2b",
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
                    } """
            ),
            status = Status.VENTER_GODKJENNING,
            kommunenavn = null,
            soknadGjelder = "Søknad om Hjelpemidler",
        )
        coEvery { hentSøknadData(UUID.fromString(soknadIdDuplikat)) } returns SøknadData(
            "fnrBruker",
            "fornavn etternavn",
            "fnrInnsender",
            UUID.fromString(soknadIdDuplikat),
            ObjectMapper().readTree(
                """ {
                    "fnrBruker": "fnrBruker",
                    "soknadId": "$soknadIdDuplikat",
                    "datoOpprettet": "2021-02-23T09:46:45.146+00:00",
                    "soknad": {
                        "id": "e8dac11d-fa66-4561-89d7-88a62ab31c2b",
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
                    } """
            ),
            status = Status.GODKJENT,
            kommunenavn = null,
            soknadGjelder = "Søknad om Hjelpemidler",
        )
    }

    private val rapid = TestRapid().apply {
        GodkjennSoknad(this, mock)
    }

    @BeforeEach
    fun reset() {
        rapid.reset()
        capturedSoknadId.clear()
        capturedStatus.clear()
    }

    @Test
    fun `Do not forward already godkjent søknad`() {

        val okPacket = """
            {
                "eventName": "godkjentAvBruker",
                "fodselNrBruker": "fnrBruker",
                "soknadId": "$soknadIdDuplikat"
            }
        """.trimMargin()

        rapid.sendTestMessage(okPacket)

        Thread.sleep(1000)

        val inspektør = rapid.inspektør

        inspektør.size shouldBeExactly 0
    }

    @Test
    fun `Update soknad and forward if packet contains required keys`() {

        val okPacket = """
            {
                "eventName": "godkjentAvBruker",
                "fodselNrBruker": "fnrBruker",
                "soknadId": "$soknadId"
            }
        """.trimMargin()

        rapid.sendTestMessage(okPacket)

        capturedSoknadId.captured.toString() shouldBe soknadId
        capturedStatus.captured shouldBe Status.GODKJENT

        Thread.sleep(1000)

        val inspektør = rapid.inspektør

        inspektør.size shouldBeExactly 1

        inspektør.key(0) shouldBe "fnrBruker"
        val jsonNode = inspektør.message(0)

        jsonNode["soknadId"].textValue() shouldBe soknadId
        jsonNode["fnrBruker"].textValue() shouldBe "fnrBruker"
        jsonNode["eventName"].textValue() shouldBe "hm-søknadGodkjentAvBrukerMottatt"
        jsonNode["opprettet"].textValue() shouldNotBe null
        jsonNode["soknad"] shouldNotBe null
    }

    @Test
    fun `Do not react to events without soknadId key`() {

        val invalidPacket = """
            {
                "eventName": "godkjentAvBruker",
                "fodselNrBruker": "fnrBruker"
            }
        """.trimMargin()

        Assertions.assertThrows(RiverRequiredKeyMissingException::class.java) {
            rapid.sendTestMessage(invalidPacket)
        }
    }

    @Test
    fun `Do not react to events with irrelevant eventName`() {

        val invalidPacket = """
            {
                "eventName": "theseAreNotTheEventsYouAreLookingFor",
                "fodselNrBruker": "fnrBruker",
                "soknadId": "$soknadId"
            }
        """.trimMargin()

        rapid.sendTestMessage(invalidPacket)

        verify { mock wasNot Called }
    }
}
