package no.nav.hjelpemidler.soknad.mottak.service

import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.Called
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.hjelpemidler.soknad.mottak.db.SoknadStore
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class SoknadUtenFullmaktDataSinkTest {
    private val capturedSoknadData = slot<SoknadData>()
    private val mock = mockk<SoknadStore>().apply {
        every { save(capture(capturedSoknadData)) } returns 1
    }

    private val rapid = TestRapid().apply {
        SoknadUtenFullmaktDataSink(this, mock)
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
                    "eventName": "soknadUtenFullmakt",
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
                        }
                }
        """.trimMargin()

        rapid.sendTestMessage(okPacket)

        capturedSoknadData.captured.fnrBruker shouldBe "fnrBruker"
        capturedSoknadData.captured.fnrInnsender shouldBe "fodselNrInnsender"
        capturedSoknadData.captured.status shouldBe Status.VENTER_GODKJENNING
    }

    @Test
    fun `Do not react to events without "event_name" key`() {

        val invalidPacket =
            """
                {
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
                        }
                }
        """.trimMargin()

        rapid.sendTestMessage(invalidPacket)

        verify { mock wasNot Called }
    }

    @Test
    fun `Do not react to events with irrelevant eventName`() {

        val invalidPacket =
            """
                {
                    "eventName": "soknadMedFullmakt",
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
                        }
                }
        """.trimMargin()

        rapid.sendTestMessage(invalidPacket)

        verify { mock wasNot Called }
    }

    @Test
    fun `Handle soknad if packet contains required keys`() {

        val okPacket =
            """
                {
                    "eventName": "soknadUtenFullmakt",
                    "eventId": "62f68547-11ae-418c-8ab7-4d2af985bcd8",
                    "fodselNrBruker": "fnrBruker",
                    "fodselNrInnsender": "fnrInnsender",
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
                        }
                }
        """.trimMargin()

        rapid.sendTestMessage(okPacket)

        Thread.sleep(1000)

        val inspektør = rapid.inspektør

        inspektør.size shouldBeExactly 1

        inspektør.key(0) shouldBe "fnrBruker"
        val jsonNode = inspektør.message(0)

        jsonNode["soknadId"].isNull shouldBe false
        jsonNode["fodselNrBruker"].textValue() shouldBe "fnrBruker"
        jsonNode["@event_name"].textValue() shouldBe "SøknadTilGodkjenning"
        jsonNode["@opprettet"].textValue() shouldNotBe null
    }

    @Test
    fun `Does not handle packet with soknadId`() {

        val forbiddenPacket =
            """
                {
                    "eventName": "soknadUtenFullmakt",
                    "eventId": "62f68547-11ae-418c-8ab7-4d2af985bcd8",
                    "soknadId": "id",
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
                                    "id": "62f68547-11ae-418c-8ab7-4d2af985bcd9"
                                }
                        }
                }
        """.trimMargin()

        rapid.sendTestMessage(forbiddenPacket)
        verify { mock wasNot Called }
    }

    @Test
    fun `Fail on message with lacking interesting key `() {

        val forbiddenPacket =
            """
                {
                    "eventName": "soknadUtenFullmakt",
                    "eventId": "62f68547-11ae-418c-8ab7-4d2af985bcd8",
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
                                    "id": "62f68547-11ae-418c-8ab7-4d2af985bcd9"
                                }
                        }
                }
        """.trimMargin()

        assertThrows(RuntimeException::class.java) {
            rapid.sendTestMessage(forbiddenPacket)
        }
    }
}
