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
import no.nav.hjelpemidler.behovsmeldingsmodell.Behovsmeldingsgrunnlag
import no.nav.hjelpemidler.soknad.mottak.client.SøknadsbehandlingClient
import no.nav.hjelpemidler.soknad.mottak.soknadsbehandling.SøknadsbehandlingService
import no.nav.hjelpemidler.soknad.mottak.test.Json
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class BehovsmeldingTilBrukerbekreftelseDataSinkTest {
    private val capturedGrunnlag = slot<Behovsmeldingsgrunnlag.Digital>()
    private val mock = mockk<SøknadsbehandlingClient>().apply {
        coEvery { finnSøknad(any(), any()) } returns null
        coEvery { lagreBehovsmelding(capture(capturedGrunnlag)) } returns 1
    }

    private val rapid = TestRapid().apply {
        BehovsmeldingTilBrukerbekreftelseDataSink(this, SøknadsbehandlingService(mock), mockk(relaxed = true))
    }

    @BeforeEach
    fun reset() {
        rapid.reset()
        capturedGrunnlag.clear()
    }

    @Test
    fun `Save søknad and mapping if packet contains required keys`() {
        val okPacket = Json(
            """
                {
                  "eventName": "nySoknad",
                  "signatur": "BRUKER_BEKREFTER",
                  "eventId": "62f68547-11ae-418c-8ab7-4d2af985bcd8",
                  "fodselNrBruker": "fnrBruker",
                  "fodselNrInnsender": "fodselNrInnsender",
                  "soknad": {
                    "soknad": {
                      "date": "2020-06-19",
                      "bruker": {
                        "fornavn": "fornavn",
                        "etternavn": "etternavn"
                      },
                      "id": "62f68547-11ae-418c-8ab7-4d2af985bcd8"
                    }
                  },
                  "kommunenavn": "Oslo"
                }
            """.trimIndent()
        )

        rapid.sendTestMessage(okPacket.toString())

        capturedGrunnlag.captured.fnrBruker shouldBe "fnrBruker"
        capturedGrunnlag.captured.fnrInnsender shouldBe "fodselNrInnsender"
        capturedGrunnlag.captured.status shouldBe BehovsmeldingStatus.VENTER_GODKJENNING
    }

    @Test
    fun `Do not react to events without event_name key`() {
        val invalidPacket = Json(
            """
                {
                  "signatur": "BRUKER_BEKREFTER",
                  "eventId": "62f68547-11ae-418c-8ab7-4d2af985bcd8",
                  "fodselNrBruker": "fnrBruker",
                  "fodselNrInnsender": "fodselNrInnsender",
                  "soknad": {
                    "soknad": {
                      "date": "2020-06-19",
                      "bruker": {
                        "fornavn": "fornavn",
                        "etternavn": "etternavn"
                      },
                      "id": "62f68547-11ae-418c-8ab7-4d2af985bcd8"
                    }
                  }
                }
            """.trimIndent()
        )

        rapid.sendTestMessage(invalidPacket.toString())

        verify { mock wasNot Called }
    }

    @Test
    fun `Do not react to events with irrelevant signature`() {
        val invalidPacket = Json(
            """
                {
                  "eventName": "nySoknad",
                  "signatur": "FULLMAKT",
                  "eventId": "62f68547-11ae-418c-8ab7-4d2af985bcd8",
                  "fodselNrBruker": "fnrBruker",
                  "fodselNrInnsender": "fodselNrInnsender",
                  "soknad": {
                    "soknad": {
                      "date": "2020-06-19",
                      "bruker": {
                        "fornavn": "fornavn",
                        "etternavn": "etternavn"
                      },
                      "id": "62f68547-11ae-418c-8ab7-4d2af985bcd8"
                    }
                  }
                }
            """.trimIndent()
        )

        rapid.sendTestMessage(invalidPacket.toString())

        verify { mock wasNot Called }
    }

    @Test
    fun `Handle søknad if packet contains required keys`() {
        val okPacket = Json(
            """
                {
                  "eventName": "nySoknad",
                  "signatur": "BRUKER_BEKREFTER",
                  "eventId": "62f68547-11ae-418c-8ab7-4d2af985bcd8",
                  "fodselNrBruker": "fnrBruker",
                  "fodselNrInnsender": "fnrInnsender",
                  "soknad": {
                    "soknad": {
                      "date": "2020-06-19",
                      "bruker": {
                        "fornavn": "fornavn",
                        "etternavn": "etternavn"
                      },
                      "id": "62f68547-11ae-418c-8ab7-4d2af985bcd8"
                    }
                  },
                  "kommunenavn": "Oslo"
                }
            """.trimIndent()
        )

        rapid.sendTestMessage(okPacket.toString())

        val inspektør = rapid.inspektør

        inspektør.size shouldBeExactly 1

        inspektør.key(0) shouldBe "fnrBruker"
        val jsonNode = inspektør.message(0)

        jsonNode["soknadId"].isNull shouldBe false
        jsonNode["fnrBruker"].textValue() shouldBe "fnrBruker"
        jsonNode["eventName"].textValue() shouldBe "hm-SøknadTilGodkjenning"
        jsonNode["opprettet"].textValue() shouldNotBe null
    }

    @Test
    fun `Does not handle packet with søknadId`() {
        val forbiddenPacket = Json(
            """
                {
                  "eventName": "nySoknad",
                  "signatur": "BRUKER_BEKREFTER",
                  "eventId": "62f68547-11ae-418c-8ab7-4d2af985bcd8",
                  "soknadId": "id",
                  "fodselNrBruker": "fnrBruker",
                  "fodselNrInnsender": "fodselNrInnsender",
                  "soknad": {
                    "soknad": {
                      "date": "2020-06-19",
                      "bruker": {
                        "fornavn": "fornavn",
                        "etternavn": "etternavn"
                      },
                      "id": "62f68547-11ae-418c-8ab7-4d2af985bcd9"
                    }
                  },
                  "kommunenavn": "Oslo"
                }
            """.trimIndent()
        )

        assertThrows(RiverRequiredKeyMissingException::class.java) {
            rapid.sendTestMessage(forbiddenPacket.toString())
        }
    }

    @Test
    fun `Fail on message with lacking interesting key `() {
        val forbiddenPacket = Json(
            """
                {
                  "eventName": "nySoknad",
                  "signatur": "BRUKER_BEKREFTER",
                  "eventId": "62f68547-11ae-418c-8ab7-4d2af985bcd8",
                  "fodselNrInnsender": "fodselNrInnsender",
                  "soknad": {
                    "soknad": {
                      "date": "2020-06-19",
                      "bruker": {
                        "fornavn": "fornavn",
                        "etternavn": "etternavn"
                      },
                      "id": "62f68547-11ae-418c-8ab7-4d2af985bcd9"
                    }
                  },
                  "kommunenavn": "Oslo"
                }
            """.trimIndent()
        )

        assertThrows(RiverRequiredKeyMissingException::class.java) {
            rapid.sendTestMessage(forbiddenPacket.toString())
        }
    }
}
