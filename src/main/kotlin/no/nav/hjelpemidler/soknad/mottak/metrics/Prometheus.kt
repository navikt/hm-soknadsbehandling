package no.nav.hjelpemidler.soknad.mottak.metrics

import io.prometheus.client.CollectorRegistry
import io.prometheus.client.Counter
import io.prometheus.client.Histogram

internal object Prometheus {
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

    val ordrelinjeLagretOgSendtTilRapidCounter = Counter
        .build()
        .name("hm_oebs_ordrelinje_sendt_til_rapid")
        .help("Antall OEBS-ordrelinjer lagret og sendt til rapid")
        .register(collectorRegistry)
}
