package no.nav.hjelpemidler.soknad.mottak.metrics.kommune

import com.github.kittinunf.fuel.core.FuelError
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class KommuneServiceTest {

    private val kommuneService = KommuneService()

    @Test
    fun kommunenrTilSted() {
        val kommune = kommuneService.kommunenrTilSted("3005")
        assertEquals("DRAMMEN", kommune?.kommunenavn)
        assertEquals("VIKEN", kommune?.fylkenavn)
    }

    @Test
    fun `skal kaste exception når oppslag feiler`() {
        val ugyldigUrl = "http://localhost:8089/ugyldig"

        assertThrows<FuelError> {
            KommuneService(OppslagClient(ugyldigUrl))
        }
    }
}
