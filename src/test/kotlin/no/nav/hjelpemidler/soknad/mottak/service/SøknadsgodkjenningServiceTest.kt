package no.nav.hjelpemidler.soknad.mottak.service

import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.hjelpemidler.soknad.mottak.client.SøknadForRiverClient
import no.nav.hjelpemidler.soknad.mottak.river.GodkjennSoknad
import org.junit.jupiter.api.Test
import java.util.UUID

internal class SøknadsgodkjenningServiceTest {
    private val capturedSøknadId = slot<UUID>()

    val soknadId = UUID.fromString("62f68547-11ae-418c-8ab7-4d2af985bcd8")

    private val mockSoknad =
        ObjectMapper().readTree(
            """
                {
                    "soknad": {
                        "date": "2020-06-19",
                        "bruker": {
                            "fornavn": "fornavn",
                            "etternavn": "etternavn"
                        },
                        "id": "62f68547-11ae-418c-8ab7-4d2af985bcd8"
                    }
                }
            """
        )

    private val mock = mockk<SøknadForRiverClient>().apply {
        coEvery { oppdaterStatus(soknadId, Status.GODKJENT) } returns 1
        coEvery { hentSøknadData(capture(capturedSøknadId)) } returns SøknadData(
            "123",
            "navn",
            "234",
            soknadId,
            mockSoknad,
            Status.VENTER_GODKJENNING,
            "oslo",
            soknadGjelder = "Søknad om Hjelpemidler",
        )
    }

    private val rapid = TestRapid().apply {
        GodkjennSoknad(this, mock)
    }

    @Test
    fun `Søknad blir godkjent`() {

        val okPacket = """
            {
                "eventName": "godkjentAvBruker",
                "soknadId": "62f68547-11ae-418c-8ab7-4d2af985bcd8"
            }
        """.trimMargin()

        rapid.sendTestMessage(okPacket)

        coVerify {
            mock.oppdaterStatus(soknadId, Status.GODKJENT)
            mock.hentSøknadData(soknadId)
        }
    }
}
