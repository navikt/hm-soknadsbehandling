package no.nav.hjelpemidler.soknad.mottak.service

import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

fun periodeMellomDatoer(fraDato: LocalDateTime, tilDato: LocalDateTime): String {
    var tempDateTime = LocalDateTime.from(fraDato)

    val days: Long = tempDateTime.until(tilDato, ChronoUnit.DAYS)
    tempDateTime = tempDateTime.plusDays(days)

    val hours: Long = tempDateTime.until(tilDato, ChronoUnit.HOURS)
    tempDateTime = tempDateTime.plusHours(hours)

    val minutes: Long = tempDateTime.until(tilDato, ChronoUnit.MINUTES)
    tempDateTime = tempDateTime.plusMinutes(minutes)

    val seconds: Long = tempDateTime.until(tilDato, ChronoUnit.SECONDS)

    return "$days dager, $hours timer, $minutes minutter, $seconds sekunder"
}
