package no.nav.hjelpemidler.soknad.mottak.river

import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.hjelpemidler.behovsmeldingsmodell.BehovsmeldingStatus
import no.nav.hjelpemidler.behovsmeldingsmodell.Behovsmeldingsgrunnlag
import no.nav.hjelpemidler.behovsmeldingsmodell.sak.InfotrygdSakId
import no.nav.hjelpemidler.soknad.mottak.client.SøknadsbehandlingClient
import no.nav.hjelpemidler.soknad.mottak.metrics.Metrics
import no.nav.hjelpemidler.soknad.mottak.soknadsbehandling.SøknadsbehandlingService
import no.nav.hjelpemidler.soknad.mottak.test.Json
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class PapirsøknadEndeligJournalførtTest {
    private val capturedGrunnlag = slot<Behovsmeldingsgrunnlag.Papir>()
    private val mock = mockk<SøknadsbehandlingClient> {
        coEvery { lagreBehovsmelding(capture(capturedGrunnlag)) } returns 1
    }
    private val metricsMock = mockk<Metrics>(relaxed = true)

    private val rapid = TestRapid().apply {
        PapirsøknadEndeligJournalført(this, SøknadsbehandlingService(mock), metricsMock)
    }

    @BeforeEach
    fun reset() {
        rapid.reset()
        capturedGrunnlag.clear()
    }

    @Test
    fun `Lagring av papirsøknad ved endelig journalført event`() {
        val okPacket = Json(
            """
                {
                  "eventId": "6040ede0-e926-4642-9251-fa04dc5f6e7d",
                  "eventName": "PapirSoeknadEndeligJournalfoert",
                  "fodselNrBruker": "10127622634",
                  "hendelse": {
                    "journalingEvent": {
                      "behandlingstema": "",
                      "hendelsesId": "f7bbf681-4ae9-4874-8fd1-5a6bf656ebcb",
                      "hendelsesType": "EndeligJournalført",
                      "journalpostId": 453647364,
                      "journalpostStatus": "J",
                      "kanalReferanseId": "081b4dbf-602a-448d-97c7-3a0126fb8d3eHJE-DIGITAL-SOKNAD",
                      "mottaksKanal": "NAV_NO",
                      "temaGammelt": "HJE",
                      "temaNytt": "HJE",
                      "versjon": 1
                    },
                    "journalingEventSAF": {
                      "avsenderMottaker": {
                        "erLikBruker": true,
                        "id": "10127622634",
                        "land": "NORGE",
                        "navn": "KRAFTIG ERT",
                        "type": "FNR"
                      },
                      "bruker": {
                        "id": "10127622634",
                        "type": "FNR"
                      },
                      "dokumenter": [
                        {
                          "brevkode": "NAV 10-07.03"
                        }
                      ],
                      "journalfoerendeEnhet": "4703",
                      "journalpostId": "453647364",
                      "journalposttype": "I",
                      "journalstatus": "JOURNALFOERT",
                      "kanal": "NAV_NO",
                      "sak": {
                        "arkivsaksnummer": "140267930",
                        "arkivsaksystem": "GSAK",
                        "fagsakId": "4203A05",
                        "fagsaksystem": "IT01"
                      },
                      "tema": "HJE",
                      "tittel": "Søknad om hjelpemidler"
                    }
                  },
                  "opprettet": "2021-04-08T15:56:11.457073862",
                  "soknadId": "081b4dbf-602a-448d-97c7-3a0126fb8d3e"
                }
            """.trimIndent()
        )

        rapid.sendTestMessage(okPacket.toString())

        capturedGrunnlag.captured.fnrBruker shouldBe "10127622634"
        capturedGrunnlag.captured.journalpostId shouldBe "453647364"
        capturedGrunnlag.captured.status shouldBe BehovsmeldingStatus.ENDELIG_JOURNALFØRT
        capturedGrunnlag.captured.navnBruker shouldBe "KRAFTIG ERT"

        capturedGrunnlag.captured.sakstilknytning?.sakId shouldBe InfotrygdSakId("4203A05")
    }
}
