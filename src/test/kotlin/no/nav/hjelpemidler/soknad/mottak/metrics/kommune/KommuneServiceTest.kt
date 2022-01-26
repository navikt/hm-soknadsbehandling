package no.nav.hjelpemidler.soknad.mottak.metrics.kommune

import com.github.kittinunf.fuel.core.FuelError
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class KommuneServiceTest {

    @Test
    fun `skal kaste exception når oppslag feiler`() {
        val ugyldigUrl = "http://localhost:8089/ugyldig"

        assertThrows<FuelError> {
            KommuneService(OppslagClient(ugyldigUrl))
        }
    }
}
