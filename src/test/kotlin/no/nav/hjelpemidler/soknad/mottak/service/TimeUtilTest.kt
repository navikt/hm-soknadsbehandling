package no.nav.hjelpemidler.soknad.mottak.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

internal class TimeUtilTest {

    @Test
    fun `Finn periode mellom to datoer`() {
        val periode = periodeMellomDatoer(
            LocalDateTime.of(2020, 1, 1, 1, 1),
            LocalDateTime.of(2020, 2, 15, 2, 2)
        )
        assertEquals("45 dager, 1 timer, 1 minutter, 0 sekunder", periode)
    }
}
