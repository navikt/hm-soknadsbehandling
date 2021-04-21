package no.nav.hjelpemidler.soknad.mottak.service

import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.hjelpemidler.soknad.mottak.client.SøknadForRiverClient
import no.nav.hjelpemidler.soknad.mottak.river.PapirSøknadEndeligJournalført
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class PapirsoknadEndeligJournalfortTest {
    private val capturedInfotrygdMock = slot<VedtaksresultatData>()
    private val capturedSoknadData = slot<PapirSøknadData>()
    private val mock = mockk<SøknadForRiverClient>().apply {
        coEvery { savePapir(capture(capturedSoknadData)) } returns 1
        coEvery { soknadFinnes(any()) } returns false
        coEvery { fnrOgJournalpostIdFinnes(any(), any()) } returns false
        coEvery { lagKnytningMellomFagsakOgSøknad(capture(capturedInfotrygdMock)) } returns 1
    }

    private val rapid = TestRapid().apply {
        PapirSøknadEndeligJournalført(this, mock)
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
                    "eventId":"6040ede0-e926-4642-9251-fa04dc5f6e7d",
                    "eventName":"PapirSoeknadEndeligJournalfoert",
                    "fodselNrBruker":"10127622634",
                    "hendelse":{
                        "journalingEvent":{
                            "behandlingstema":"",
                            "hendelsesId":"f7bbf681-4ae9-4874-8fd1-5a6bf656ebcb",
                            "hendelsesType":"EndeligJournalført",
                            "journalpostId":453647364,
                            "journalpostStatus":"J",
                            "kanalReferanseId":"081b4dbf-602a-448d-97c7-3a0126fb8d3eHJE-DIGITAL-SOKNAD",
                            "mottaksKanal":"NAV_NO",
                            "temaGammelt":"HJE",
                            "temaNytt":"HJE",
                            "versjon":1
                        },
                        "journalingEventSAF":{
                            "avsenderMottaker":{
                                "erLikBruker":true,
                                "id":"10127622634",
                                "land":"NORGE",
                                "navn":"KRAFTIG ERT",
                                "type":"FNR"
                            },
                            "bruker":{
                                "id":"10127622634",
                                "type":"FNR"
                            },
                            "dokumenter":[
                                {
                                    "brevkode":"NAV 10-07.03"
                                }
                            ],
                            "journalfoerendeEnhet":"4703",
                            "journalpostId":"453647364",
                            "journalposttype":"I",
                            "journalstatus":"JOURNALFOERT",
                            "kanal":"NAV_NO",
                            "sak":{
                                "arkivsaksnummer":"140267930",
                                "arkivsaksystem":"GSAK",
                                "fagsakId":"4203A05",
                                "fagsaksystem":"IT01"
                            },
                            "tema":"HJE",
                            "tittel":"Søknad om hjelpemidler"
                        }
                    },
                    "opprettet":"2021-04-08T15:56:11.457073862",
                    "soknadId":"081b4dbf-602a-448d-97c7-3a0126fb8d3e"
                }
            """.trimMargin()

        rapid.sendTestMessage(okPacket)

        capturedSoknadData.captured.fnrBruker shouldBe "10127622634"
        capturedSoknadData.captured.journalpostid shouldBe 453647364
        capturedSoknadData.captured.status shouldBe Status.ENDELIG_JOURNALFØRT
        capturedSoknadData.captured.navnBruker shouldBe "KRAFTIG ERT"

        capturedInfotrygdMock.captured.trygdekontorNr shouldBe "4203"
        capturedInfotrygdMock.captured.saksblokk shouldBe "A"
        capturedInfotrygdMock.captured.saksnr shouldBe "05"
    }
}
