package no.nav.hjelpemidler.soknad.mottak.service

import java.time.Duration
import java.time.LocalDateTime

fun periodeMellomDatoer(fraDato: LocalDateTime, tilDato: LocalDateTime): String {
    val duration = Duration.between(fraDato, tilDato)
    return "${duration.toDaysPart()} dager, ${duration.toHoursPart()} timer, ${duration.toMinutesPart()} minutter, ${duration.toSecondsPart()} sekunder"
}
