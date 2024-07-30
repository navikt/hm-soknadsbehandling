package no.nav.hjelpemidler.soknad.mottak.service

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.hjelpemidler.behovsmeldingsmodell.BehovsmeldingStatus
import no.nav.hjelpemidler.soknad.mottak.client.SøknadForRiverClient
import no.nav.hjelpemidler.soknad.mottak.river.GodkjennSoknad
import no.nav.hjelpemidler.soknad.mottak.test.lagSøknad
import no.nav.hjelpemidler.soknad.mottak.test.readTree
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Test
import java.util.UUID

class SøknadsgodkjenningServiceTest {
    private val capturedSøknadId = slot<UUID>()

    private val søknadId = UUID.randomUUID()

    private val søknad = readTree(
        """
            {
                "soknad": {
                    "date": "2020-06-19",
                    "bruker": {
                        "fornavn": "fornavn",
                        "etternavn": "etternavn"
                    },
                    "id": "$søknadId"
                }
            }
        """.trimIndent()
    )
    private val mock = mockk<SøknadForRiverClient>().apply {
        coEvery { oppdaterStatus(søknadId, BehovsmeldingStatus.GODKJENT) } returns 1
        coEvery { hentSøknad(capture(capturedSøknadId), true) } returns lagSøknad(
            søknadId = søknadId,
            status = BehovsmeldingStatus.VENTER_GODKJENNING,
            data = søknad
        )
    }

    private val rapid = TestRapid().apply {
        GodkjennSoknad(this, mock)
    }

    @Test
    fun `Søknad blir godkjent`() {
        @Language("JSON")
        val okPacket = """
            {
                "eventName": "godkjentAvBruker",
                "soknadId": "$søknadId"
            }
        """.trimIndent()

        rapid.sendTestMessage(okPacket)

        coVerify {
            mock.oppdaterStatus(søknadId, BehovsmeldingStatus.GODKJENT)
            mock.hentSøknad(søknadId, true)
        }
    }
}
