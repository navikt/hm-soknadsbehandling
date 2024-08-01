package no.nav.hjelpemidler.soknad.mottak.service

import io.kotest.matchers.shouldBe
import java.time.LocalDateTime
import kotlin.test.Test

class PeriodeMellomDatoerTest {
    @Test
    fun `Finn periode mellom to datoer`() {
        val periode = periodeMellomDatoer(
            LocalDateTime.of(2020, 1, 1, 1, 1),
            LocalDateTime.of(2020, 2, 15, 2, 2)
        )
        periode shouldBe "45 dager, 1 timer, 1 minutter, 0 sekunder"
    }
}
