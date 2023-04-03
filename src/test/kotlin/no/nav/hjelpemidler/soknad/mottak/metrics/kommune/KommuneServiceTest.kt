package no.nav.hjelpemidler.soknad.mottak.metrics.kommune

import java.net.ConnectException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class KommuneServiceTest {

    // TODO: denne er kanskje litt tullete?
    @Test
    fun `skal kaste exception når oppslag feiler`() {
        val ugyldigUrl = "http://localhost:8089/ugyldig"

        assertThrows<ConnectException> {
            KommuneService(OppslagClient(ugyldigUrl))
        }
    }
}
