package no.nav.hjelpemidler.soknad.mottak.metrics

import io.prometheus.client.CollectorRegistry
import io.prometheus.client.Counter
import io.prometheus.client.Histogram

internal object Prometheus {
    val collectorRegistry = CollectorRegistry.defaultRegistry

    val dbTimer = Histogram.build("dp_soknad_mottak_db_query_latency_histogram", "Distribution of db execution times")
        .labelNames("query")
        .register(collectorRegistry)

    val jpCounter = Counter
        .build()
        .name("dp_soknad_mottak_journal_post_mottatt_counter")
        .help("Antall journal post mottatt")
        .register(collectorRegistry)

    val soknadCounter = Counter
        .build()
        .name("dp_soknad_mottak_soknad_mottatt_counter")
        .help("Antall soknakder mottatt")
        .register(collectorRegistry)

    val soknadSendtCounter = Counter
        .build()
        .name("dp_soknad_mottak_soknad_sendt")
        .help("Antall soknakder sendt")
        .register(collectorRegistry)
}
