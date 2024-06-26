package no.nav.hjelpemidler.soknad.mottak.metrics

import io.prometheus.client.CollectorRegistry
import io.prometheus.client.Counter
import io.prometheus.client.Histogram

object Prometheus {
    val collectorRegistry = CollectorRegistry.defaultRegistry

    val dbTimer = Histogram.build("hm_soknad_mottak_db_query_latency_histogram", "Distribution of db execution times")
        .labelNames("query")
        .register(collectorRegistry)

    val soknadSendtCounter = Counter
        .build()
        .name("hm_soknad_mottak_soknad_sendt")
        .help("Antall søknader sendt")
        .register(collectorRegistry)

    val soknadMedFullmaktCounter = Counter
        .build()
        .name("hm_soknad_mottak_soknad_fullmakt")
        .help("Antall søknader med fullmakt")
        .register(collectorRegistry)

    val brukerpassbytteCounter = Counter
        .build()
        .name("hm_brukerpassbytte_mottak")
        .help("Antall brukerpassbytter")
        .register(collectorRegistry)

    val soknadTilGodkjenningCounter = Counter
        .build()
        .name("hm_soknad_mottak_soknad_til_godkjenning")
        .help("Antall søknader til godkjenning")
        .register(collectorRegistry)

    val soknadGodkjentAvBrukerCounter = Counter
        .build()
        .name("hm_soknad_mottak_godkjenning_fra_bruker")
        .help("Antall søknader godkjent")
        .register(collectorRegistry)

    val godkjenningsfristErUtløptCounter = Counter
        .build()
        .name("hm_soknad_godkjenningsfrist_utlopt")
        .help("Antall søknader der godkjenningsfrist er utløpt")
        .register(collectorRegistry)

    val soknadSlettetAvBrukerCounter = Counter
        .build()
        .name("hm_soknad_mottak_sletting_fra_bruker")
        .help("Antall søknader slettet")
        .register(collectorRegistry)

    val ordrelinjeLagretCounter = Counter
        .build()
        .name("hm_oebs_ordrelinje_lagret")
        .help("Antall OEBS-ordrelinjer lagret")
        .register(collectorRegistry)

    val vedtaksresultatLagretCounter = Counter
        .build()
        .name("hm_vedtaksresultat_lagret")
        .help("Antall vedtaksresultat fra infotrygd lagret")
        .register(collectorRegistry)

    val ordrelinjeLagretOgSendtTilRapidCounter = Counter
        .build()
        .name("hm_oebs_ordrelinje_sendt_til_rapid")
        .help("Antall OEBS-ordrelinjer lagret og sendt til rapid")
        .register(collectorRegistry)
    val knytningMellomSøknadOgInfotrygdOpprettaCounter = Counter
        .build()
        .name("hm_knytning_mellom_infotrygd_og_soeknad_oppretta")
        .help("Mengda søknadar som er knytta til ei Infotrygd-sak")
        .register(collectorRegistry)

    val knytningMellomSøknadOgInfotrygdProblemCounter = Counter
        .build()
        .name("hm_knytning_mellom_infotrygd_og_soeknad_oppretta_problem")
        .help("Mengda problem for knytning mellom søknadar og Infotrygd-sak")
        .register(collectorRegistry)
}
