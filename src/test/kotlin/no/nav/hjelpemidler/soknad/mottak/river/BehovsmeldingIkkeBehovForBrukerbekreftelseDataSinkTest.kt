package no.nav.hjelpemidler.soknad.mottak.river

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.Called
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import no.nav.hjelpemidler.behovsmeldingsmodell.BehovsmeldingStatus
import no.nav.hjelpemidler.behovsmeldingsmodell.Behovsmeldingsgrunnlag
import no.nav.hjelpemidler.soknad.mottak.client.SøknadsbehandlingClient
import no.nav.hjelpemidler.soknad.mottak.metrics.Metrics
import no.nav.hjelpemidler.soknad.mottak.soknadsbehandling.SøknadsbehandlingService
import no.nav.hjelpemidler.soknad.mottak.test.Json
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class BehovsmeldingIkkeBehovForBrukerbekreftelseDataSinkTest {
    private val capturedGrunnlag = slot<Behovsmeldingsgrunnlag.Digital>()
    private val mock = mockk<SøknadsbehandlingClient>().apply {
        coEvery { lagreBehovsmelding(capture(capturedGrunnlag)) } returns 1
    }

    private val rapid = TestRapid().apply {
        BehovsmeldingIkkeBehovForBrukerbekreftelseDataSink(
            this,
            SøknadsbehandlingService(mock),
            mockk<Metrics>(relaxed = true)
        )
    }

    @BeforeEach
    fun reset() {
        rapid.reset()
        capturedGrunnlag.clear()
    }

    @Test
    fun `Save soknad and mapping if packet contains required keys`() {
        val okPacket = Json(
            """
                {
                  "eventName": "nySoknad",
                  "signatur": "FULLMAKT",
                  "eventId": "62f68547-11ae-418c-8ab7-4d2af985bcd8",
                  "fodselNrBruker": "fnrBruker",
                  "fodselNrInnsender": "fodselNrInnsender",
                  "soknad": {
                    "behovsmeldingType": "SØKNAD",
                    "id": "62f68547-11ae-418c-8ab7-4d2af985bcd8",
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

        rapid.sendTestMessage(okPacket.toString())

        capturedGrunnlag.captured.fnrBruker shouldBe "fnrBruker"
        capturedGrunnlag.captured.fnrInnsender shouldBe "fodselNrInnsender"
        capturedGrunnlag.captured.status shouldBe BehovsmeldingStatus.GODKJENT_MED_FULLMAKT
        capturedGrunnlag.captured.behovsmeldingGjelder shouldBe "Søknad om hjelpemidler"
    }

    @Test
    fun `Save bestilling and mapping if packet contains required keys`() {
        val okPacket = Json(
            """
                {
                  "eventName": "nySoknad",
                  "signatur": "FULLMAKT",
                  "eventId": "62f68547-11ae-418c-8ab7-4d2af985bcd8",
                  "fodselNrBruker": "fnrBruker",
                  "fodselNrInnsender": "fodselNrInnsender",
                  "soknad": {
                    "behovsmeldingType": "BESTILLING",
                    "id": "62f68547-11ae-418c-8ab7-4d2af985bcd8",
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

        rapid.sendTestMessage(okPacket.toString())

        capturedGrunnlag.captured.fnrBruker shouldBe "fnrBruker"
        capturedGrunnlag.captured.fnrInnsender shouldBe "fodselNrInnsender"
        capturedGrunnlag.captured.status shouldBe BehovsmeldingStatus.GODKJENT_MED_FULLMAKT
        capturedGrunnlag.captured.behovsmeldingGjelder shouldBe "Bestilling av hjelpemidler"
    }

    @Test
    fun `Do not react to events without event_name key`() {
        val invalidPacket = Json(
            """
                {
                  "signatur": "FULLMAKT",
                  "eventId": "62f68547-11ae-418c-8ab7-4d2af985bcd8",
                  "fodselNrBruker": "fnrBruker",
                  "fodselNrInnsender": "fodselNrInnsender",
                  "soknad": {
                    "id": "62f68547-11ae-418c-8ab7-4d2af985bcd8",
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
                  "signatur": "BRUKER_BEKREFTER",
                  "eventId": "62f68547-11ae-418c-8ab7-4d2af985bcd8",
                  "fodselNrBruker": "fnrBruker",
                  "fodselNrInnsender": "fodselNrInnsender",
                  "soknad": {
                    "id": "62f68547-11ae-418c-8ab7-4d2af985bcd8",
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
    fun `Signatur element in JSON is mapped to correct status`() {
        val okPacket = Json(
            """
                {
                  "eventName": "nySoknad",
                  "signatur": "FULLMAKT",
                  "eventId": "62f68547-11ae-418c-8ab7-4d2af985bcd8",
                  "fodselNrBruker": "fnrBruker",
                  "fodselNrInnsender": "fodselNrInnsender",
                  "soknad": {
                    "id": "62f68547-11ae-418c-8ab7-4d2af985bcd8",
                    "behovsmeldingType": "SØKNAD",
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

        rapid.sendTestMessage(okPacket.toString())
        capturedGrunnlag.captured.status shouldBe BehovsmeldingStatus.GODKJENT_MED_FULLMAKT
    }

    @Test
    fun `Signatur FRITAK_FRA_FULLMAKT element in JSON is mapped to correct status`() {
        val okPacket = Json(
            """
                {
                  "eventName": "nySoknad",
                  "signatur": "FRITAK_FRA_FULLMAKT",
                  "eventId": "62f68547-11ae-418c-8ab7-4d2af985bcd8",
                  "fodselNrBruker": "fnrBruker",
                  "fodselNrInnsender": "fodselNrInnsender",
                  "soknad": {
                    "id": "62f68547-11ae-418c-8ab7-4d2af985bcd8",
                    "behovsmeldingType": "SØKNAD",
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

        rapid.sendTestMessage(okPacket.toString())
        capturedGrunnlag.captured.status shouldBe BehovsmeldingStatus.GODKJENT_MED_FULLMAKT
    }

    @Test
    fun `Byttemelding med signatur IKKE_INNHENTET_FORDI_BYTTE får riktig status`() {
        val okPacket = Json(
            """
                {
                  "eventName": "nySoknad",
                  "signatur": "IKKE_INNHENTET_FORDI_BYTTE",
                  "eventId": "62f68547-11ae-418c-8ab7-4d2af985bcd8",
                  "fodselNrBruker": "fnrBruker",
                  "fodselNrInnsender": "fodselNrInnsender",
                  "soknad": {
                    "id": "62f68547-11ae-418c-8ab7-4d2af985bcd8",
                    "behovsmeldingType": "SØKNAD",
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

        rapid.sendTestMessage(okPacket.toString())
        capturedGrunnlag.captured.status shouldBe BehovsmeldingStatus.INNSENDT_FULLMAKT_IKKE_PÅKREVD
    }

    @Test
    fun `Handle søknad if packet contains required keys`() {
        val okPacket = Json(
            """
                {
                  "eventName": "nySoknad",
                  "signatur": "FULLMAKT",
                  "eventId": "62f68547-11ae-418c-8ab7-4d2af985bcd8",
                  "fodselNrBruker": "fnrBruker",
                  "fodselNrInnsender": "fnrInnsender",
                  "soknad": {
                    "id": "62f68547-11ae-418c-8ab7-4d2af985bcd8",
                    "behovsmeldingType": "SØKNAD",
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

        rapid.sendTestMessage(okPacket.toString())

        Thread.sleep(1000)

        val inspektør = rapid.inspektør

        inspektør.size shouldBeExactly 1

        inspektør.key(0) shouldBe "fnrBruker"
        val jsonNode = inspektør.message(0)

        jsonNode["soknadId"].isNull shouldBe false
        jsonNode["fnrBruker"].textValue() shouldBe "fnrBruker"
        jsonNode["eventName"].textValue() shouldBe "hm-behovsmeldingMottatt"
        jsonNode["opprettet"].textValue() shouldNotBe null
        jsonNode["soknadGjelder"].textValue() shouldBe "Søknad om hjelpemidler"
    }

    @Test
    fun `Does not handle packet with soknadId`() {
        val forbiddenPacket = Json(
            """
                {
                  "eventName": "nySoknad",
                  "signatur": "FULLMAKT",
                  "eventId": "62f68547-11ae-418c-8ab7-4d2af985bcd8",
                  "soknadId": "id",
                  "fodselNrBruker": "fnrBruker",
                  "fodselNrInnsender": "fodselNrInnsender",
                  "soknad": {
                    "id": "62f68547-11ae-418c-8ab7-4d2af985bcd8",
                    "soknad": {
                      "date": "2020-06-19",
                      "bruker": {
                        "fornavn": "fornavn",
                        "etternavn": "etternavn"
                      },
                      "id": "62f68547-11ae-418c-8ab7-4d2af985bcd9"
                    }
                  }
                }
            """.trimIndent()
        )

        shouldThrow<RiverRequiredKeyMissingException> {
            rapid.sendTestMessage(forbiddenPacket.toString())
        }
    }

    @Test
    fun `Fail on message with lacking interesting key `() {
        val forbiddenPacket = Json(
            """
                {
                  "eventName": "nySoknad",
                  "signatur": "FULLMAKT",
                  "eventId": "62f68547-11ae-418c-8ab7-4d2af985bcd8",
                  "fodselNrInnsender": "fodselNrInnsender",
                  "soknad": {
                    "id": "62f68547-11ae-418c-8ab7-4d2af985bcd8",
                    "soknad": {
                      "date": "2020-06-19",
                      "bruker": {
                        "fornavn": "fornavn",
                        "etternavn": "etternavn"
                      },
                      "id": "62f68547-11ae-418c-8ab7-4d2af985bcd9"
                    }
                  }
                }
            """.trimIndent()
        )

        shouldThrow<RiverRequiredKeyMissingException> {
            rapid.sendTestMessage(forbiddenPacket.toString())
        }
    }

    @Test
    fun `Does fail on message lacking behovsmeldingType parameter`() {
        val okPacket = Json(
            """
                {
                  "eventName": "nySoknad",
                  "signatur": "FULLMAKT",
                  "eventId": "62f68547-11ae-418c-8ab7-4d2af985bcd8",
                  "fodselNrBruker": "fnrBruker",
                  "fodselNrInnsender": "fodselNrInnsender",
                  "soknad": {
                    "id": "62f68547-11ae-418c-8ab7-4d2af985bcd8",
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

        shouldThrow<RuntimeException> { rapid.sendTestMessage(okPacket.toString()) }
    }
}
