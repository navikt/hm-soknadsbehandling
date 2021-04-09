package no.nav.hjelpemidler.soknad.mottak.service

import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.hjelpemidler.soknad.mottak.db.InfotrygdStore
import no.nav.hjelpemidler.soknad.mottak.db.SøknadStore
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class PapirsøknadEndeligJournalførtTest {
    private val capturedSoknadData = slot<PapirSøknadData>()
    private val mock = mockk<SøknadStore>().apply {
        every { savePapir(capture(capturedSoknadData)) } returns 1
        every { soknadFinnes(any()) } returns false
    }
    private val capturedInfotrygdMock = slot<VedtaksresultatData>()
    private val mockInfotrygd = mockk<InfotrygdStore>().apply {
        every { lagKnytningMellomFagsakOgSøknad(capture(capturedInfotrygdMock)) } returns 1
    }

    private val rapid = TestRapid().apply {
        PapirSøknadEndeligJournalført(this, mock, mockInfotrygd)
    }

    @BeforeEach
    fun reset() {
        rapid.reset()
        capturedSoknadData.clear()
    }

    @Test
    fun `Lagring av papirsøknad ved endelig journalført event`() {

        val okPacket =
            """ 
                {
                    "id": "62f68547-11ae-418c-8ab7-4d2af985bcd8",
                    "eventName": "PapirSoeknadEndeligJournalfoert",
                    "opprettet": "123",
                    "fodselNrBruker": "fnrBruker",
                    "eventId": "62f68547-11ae-418c-8ab7-4d2af985bcd8",
                    "hendelse": 
                        {
                            "journalingEvent": 
                                {
                                    "hendelsesId":"hendelsesId",
                                    "versjon": 1,
                                    "hendelsesType": "type",
                                    "journalpostId": 1234567,
                                    "journalpostStatus": "status",
                                    "temaGammelt": "temaGammelt",
                                    "temaNytt": "HJE",
                                    "mottaksKanal": "kanal",
                                    "kanalReferanseId": "refid",
                                    "behandlingstema": "tema"
                                },
                            "journalingEventSAF": 
                                {
                                    "journalpostId":1234567,
                                    "sak":
                                        {
                                            "fagsakId":"4703A05"
                                        }
                                }
                        }
                }
            """.trimMargin()

        rapid.sendTestMessage(okPacket)

        capturedSoknadData.captured.fnrBruker shouldBe "fnrBruker"
        capturedSoknadData.captured.journalpostid shouldBe 1234567
        capturedSoknadData.captured.status shouldBe Status.ENDELIG_JOURNALFØRT

        capturedInfotrygdMock.captured.trygdekontorNr shouldBe "4703"
        capturedInfotrygdMock.captured.saksblokk shouldBe "A"
        capturedInfotrygdMock.captured.saksnr shouldBe "05"
    }
}
