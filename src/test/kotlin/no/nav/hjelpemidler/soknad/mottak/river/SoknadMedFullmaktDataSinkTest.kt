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
import no.nav.hjelpemidler.soknad.mottak.client.SøknadForRiverClient
import no.nav.hjelpemidler.soknad.mottak.metrics.Metrics
import no.nav.hjelpemidler.soknad.mottak.service.SoknadData
import no.nav.hjelpemidler.soknad.mottak.service.Status
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow

internal class SoknadMedFullmaktDataSinkTest {
    private val capturedSoknadData = slot<SoknadData>()
    private val mock = mockk<SøknadForRiverClient>().apply {
        coEvery { save(capture(capturedSoknadData)) } returns Unit
        coEvery { soknadFinnes(any()) } returns false
    }
    private val metricsMock = mockk<Metrics>(relaxed = true)

    private val rapid = TestRapid().apply {
        SoknadMedFullmaktDataSink(this, mock, metricsMock)
    }

    @BeforeEach
    fun reset() {
        rapid.reset()
        capturedSoknadData.clear()
    }

    @Test
    fun `Save soknad and mapping if packet contains required keys`() {
        val okPacket = """
            {
                "eventName": "nySoknad",
                "signatur": "FULLMAKT",
                "eventId": "62f68547-11ae-418c-8ab7-4d2af985bcd8",
                "fodselNrBruker": "fnrBruker",
                "fodselNrInnsender": "fodselNrInnsender",
                "soknad": 
                    {
                        "behovsmeldingType": "SØKNAD",
                        "soknad":
                            {
                                "date": "2020-06-19",
                                "bruker": 
                                    {
                                        "fornavn": "fornavn",
                                        "etternavn": "etternavn"
                                    },
                                "id": "62f68547-11ae-418c-8ab7-4d2af985bcd8"
                            }
                    },
                "kommunenavn": "Oslo"
            }
        """.trimMargin()

        rapid.sendTestMessage(okPacket)

        capturedSoknadData.captured.fnrBruker shouldBe "fnrBruker"
        capturedSoknadData.captured.fnrInnsender shouldBe "fodselNrInnsender"
        capturedSoknadData.captured.status shouldBe Status.GODKJENT_MED_FULLMAKT
        capturedSoknadData.captured.soknadGjelder shouldBe "Søknad om hjelpemidler"
    }

    @Test
    fun `Save bestilling and mapping if packet contains required keys`() {
        val okPacket = """
            {
                "eventName": "nySoknad",
                "signatur": "FULLMAKT",
                "eventId": "62f68547-11ae-418c-8ab7-4d2af985bcd8",
                "fodselNrBruker": "fnrBruker",
                "fodselNrInnsender": "fodselNrInnsender",
                "soknad": 
                    {
                        "behovsmeldingType": "BESTILLING",
                        "soknad":
                            {
                                "date": "2020-06-19",
                                "bruker": 
                                    {
                                        "fornavn": "fornavn",
                                        "etternavn": "etternavn"
                                    },
                                "id": "62f68547-11ae-418c-8ab7-4d2af985bcd8"
                            }
                    },
                "kommunenavn": "Oslo"
            }
        """.trimMargin()

        rapid.sendTestMessage(okPacket)

        capturedSoknadData.captured.fnrBruker shouldBe "fnrBruker"
        capturedSoknadData.captured.fnrInnsender shouldBe "fodselNrInnsender"
        capturedSoknadData.captured.status shouldBe Status.GODKJENT_MED_FULLMAKT
        capturedSoknadData.captured.soknadGjelder shouldBe "Bestilling av hjelpemidler"
    }

    @Test
    fun `Do not react to events without event_name key`() {

        val invalidPacket = """
            {
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
        """.trimMargin()

        rapid.sendTestMessage(invalidPacket)

        verify { mock wasNot Called }
    }

    @Test
    fun `Do not react to events with irrelevant signature`() {

        val invalidPacket = """
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
                }
            }
        """.trimMargin()

        rapid.sendTestMessage(invalidPacket)

        verify { mock wasNot Called }
    }

    @Test
    fun `Signatur element in JSON is mapped to correct status`() {

        val okPacket = """
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
                },
                "kommunenavn": "Oslo"
            }
        """.trimMargin()

        rapid.sendTestMessage(okPacket)
        capturedSoknadData.captured.status shouldBe Status.GODKJENT_MED_FULLMAKT
    }

    @Test
    fun `Signatur FRITAK_FRA_FULLMAKT element in JSON is mapped to correct status`() {

        val okPacket = """
            {
                "eventName": "nySoknad",
                "signatur": "FRITAK_FRA_FULLMAKT",
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
        """.trimMargin()

        rapid.sendTestMessage(okPacket)
        capturedSoknadData.captured.status shouldBe Status.GODKJENT_MED_FULLMAKT
    }

    @Test
    fun `Handle soknad if packet contains required keys`() {

        val okPacket = """
            {
                "eventName": "nySoknad",
                "signatur": "FULLMAKT",
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
        """.trimMargin()

        rapid.sendTestMessage(okPacket)

        Thread.sleep(1000)

        val inspektør = rapid.inspektør

        inspektør.size shouldBeExactly 1

        inspektør.key(0) shouldBe "fnrBruker"
        val jsonNode = inspektør.message(0)

        jsonNode["soknadId"].isNull shouldBe false
        jsonNode["fnrBruker"].textValue() shouldBe "fnrBruker"
        jsonNode["eventName"].textValue() shouldBe "hm-søknadMedFullmaktMottatt"
        jsonNode["opprettet"].textValue() shouldNotBe null
        jsonNode["navnBruker"].textValue() shouldBe "etternavn fornavn"
        jsonNode["soknadGjelder"].textValue() shouldBe "Søknad om hjelpemidler"
    }

    @Test
    fun `Does not handle packet with soknadId`() {

        val forbiddenPacket = """
            {
                "eventName": "nySoknad",
                "signatur": "FULLMAKT",
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
        """.trimMargin()

        assertThrows(RiverRequiredKeyMissingException::class.java) {
            rapid.sendTestMessage(forbiddenPacket)
        }
    }

    @Test
    fun `Fail on message with lacking interesting key `() {
        val forbiddenPacket = """
            {
                "eventName": "nySoknad",
                "signatur": "FULLMAKT",
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
        """.trimMargin()

        assertThrows(RiverRequiredKeyMissingException::class.java) {
            rapid.sendTestMessage(forbiddenPacket)
        }
    }

    @Test
    fun `Does not fail on message lacking behovsmeldingType parameter`() {
        val okPacket = """
            {
                "eventName": "nySoknad",
                "signatur": "FULLMAKT",
                "eventId": "62f68547-11ae-418c-8ab7-4d2af985bcd8",
                "fodselNrBruker": "fnrBruker",
                "fodselNrInnsender": "fodselNrInnsender",
                "soknad": 
                    {
                        "soknad":
                            {
                                "date": "2020-06-19",
                                "bruker": 
                                    {
                                        "fornavn": "fornavn",
                                        "etternavn": "etternavn"
                                    },
                                "id": "62f68547-11ae-418c-8ab7-4d2af985bcd8"
                            }
                    },
                "kommunenavn": "Oslo"
            }
        """.trimMargin()

        assertDoesNotThrow { rapid.sendTestMessage(okPacket) }
        capturedSoknadData.captured.soknadGjelder shouldBe "Søknad om hjelpemidler"
    }
}
