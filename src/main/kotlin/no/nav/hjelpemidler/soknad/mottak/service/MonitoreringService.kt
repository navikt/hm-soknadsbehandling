package no.nav.hjelpemidler.soknad.mottak.service

import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import mu.KLogger
import mu.KotlinLogging
import no.nav.hjelpemidler.soknad.mottak.client.SøknadForRiverClient
import no.nav.hjelpemidler.soknad.mottak.metrics.Prometheus.søknaderSomManglerOppgaveGauge
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Date
import java.util.Timer
import java.util.concurrent.TimeUnit
import kotlin.concurrent.timerTask

private const val TO_DAGER = 2

internal class MonitoreringService(
    private val søknadForRiverClient: SøknadForRiverClient,
    startTime: Date = midnatt(),
    private val logger: KLogger = KotlinLogging.logger {}
) {

    init {
        Timer("MonitoreringService", true).schedule(
            timerTask {
                runBlocking {
                    launch {
                        rapporterGodkjenteSøknaderUtenOppgave()
                    }
                }
            },
            startTime,
            TimeUnit.DAYS.toMillis(1)
        )
    }

    /*
    Dette er saker som har stoppet opp pga manglende verdier i innsendingen.
    Feilen skal ha blitt rettet, og formidler har blitt informert om at de må sende inn på nytt.
    Det må vurderes konsekvens av å slette disse fra systemet dersom vi evt. skal gjøre det.
    F.eks.: Er sakene synlig for bruker?
     */
    private val ignoreList = listOf("bdd3b385-9add-4317-bf82-d8df0e6974e0", "ae15f3a2-f74b-4465-a636-7f527c98d0a0")

    internal suspend fun rapporterGodkjenteSøknaderUtenOppgave() {
        logger.info { "Starter sjekk på om det finnes godkjente søknader som mangler oppgave" }
        try {
            val godkjenteSøknaderUtenOppgave = søknadForRiverClient.hentGodkjenteSøknaderUtenOppgaveEldreEnn(TO_DAGER)
                .filter { it !in ignoreList }

            if (godkjenteSøknaderUtenOppgave.isNotEmpty()) {
                logger.error {
                    "Det finnes ${godkjenteSøknaderUtenOppgave.size} godkjente søknader uten oppgave som er eldre enn $TO_DAGER dager. " +
                        "Undersøk hvorfor disse har stoppet opp i systemet. " +
                        "søknads-IDer (max 10): ${godkjenteSøknaderUtenOppgave.take(10).joinToString()}"
                }
            }
            // Trigger alerts til Slack ved å følge med på denne Prometheus-metrikken
            søknaderSomManglerOppgaveGauge.set(godkjenteSøknaderUtenOppgave.size.toDouble())
        } catch (e: Exception) {
            logger.error(e) { "Feil under rapportering av godkjente søknader som mangler oppgave." }
        }
    }
}

private fun midnatt() = LocalDate.now().plusDays(1).atStartOfDay().toDate()

private fun LocalDateTime.toDate(): Date {
    return Date.from(this.atZone(ZoneId.systemDefault()).toInstant())
}
