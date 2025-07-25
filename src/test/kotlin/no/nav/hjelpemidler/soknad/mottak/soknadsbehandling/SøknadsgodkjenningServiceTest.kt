package no.nav.hjelpemidler.soknad.mottak.soknadsbehandling

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import no.nav.hjelpemidler.behovsmeldingsmodell.BehovsmeldingStatus
import no.nav.hjelpemidler.behovsmeldingsmodell.Statusendring
import no.nav.hjelpemidler.soknad.mottak.client.SøknadsbehandlingClient
import no.nav.hjelpemidler.soknad.mottak.river.GodkjennSøknad
import no.nav.hjelpemidler.soknad.mottak.test.Json
import no.nav.hjelpemidler.soknad.mottak.test.lagSøknad
import org.junit.jupiter.api.Test
import java.util.UUID

class SøknadsgodkjenningServiceTest {
    private val capturedSøknadId = slot<UUID>()
    private val søknadId = UUID.randomUUID()
    private val mock = mockk<SøknadsbehandlingClient>().apply {
        coEvery { oppdaterStatus(søknadId, Statusendring(BehovsmeldingStatus.GODKJENT, null, null)) } returns 1
        coEvery { hentSøknad(capture(capturedSøknadId)) } returns lagSøknad(
            søknadId = søknadId,
            status = BehovsmeldingStatus.VENTER_GODKJENNING,
        )
    }

    private val rapid = TestRapid().apply {
        GodkjennSøknad(this, SøknadsbehandlingService(mock))
    }

    @Test
    fun `Søknad blir godkjent`() {
        val okPacket = Json(
            """
                {
                    "eventName": "godkjentAvBruker",
                    "soknadId": "$søknadId"
                }
            """.trimIndent()
        )

        rapid.sendTestMessage(okPacket.toString())

        coVerify {
            mock.oppdaterStatus(søknadId, Statusendring(BehovsmeldingStatus.GODKJENT, null, null))
            mock.hentSøknad(søknadId)
        }
    }
}
