package no.nav.hjelpemidler.soknad.mottak.metrics

import io.prometheus.client.CollectorRegistry
import io.prometheus.client.Counter

object Prometheus {
    val collectorRegistry: CollectorRegistry = CollectorRegistry.defaultRegistry

    val søknadSendtCounter: Counter = Counter
        .build()
        .name("hm_soknad_mottak_soknad_sendt")
        .help("Antall søknader sendt")
        .register(collectorRegistry)

    val søknadMedFullmaktCounter: Counter = Counter
        .build()
        .name("hm_soknad_mottak_soknad_fullmakt")
        .help("Antall søknader med fullmakt")
        .register(collectorRegistry)

    val brukerpassbytteCounter: Counter = Counter
        .build()
        .name("hm_brukerpassbytte_mottak")
        .help("Antall brukerpassbytter")
        .register(collectorRegistry)

    val søknadTilGodkjenningCounter: Counter = Counter
        .build()
        .name("hm_soknad_mottak_soknad_til_godkjenning")
        .help("Antall søknader til godkjenning")
        .register(collectorRegistry)

    val søknadGodkjentAvBrukerCounter: Counter = Counter
        .build()
        .name("hm_soknad_mottak_godkjenning_fra_bruker")
        .help("Antall søknader godkjent")
        .register(collectorRegistry)

    val godkjenningsfristErUtløptCounter: Counter = Counter
        .build()
        .name("hm_soknad_godkjenningsfrist_utlopt")
        .help("Antall søknader der godkjenningsfrist er utløpt")
        .register(collectorRegistry)

    val søknadSlettetAvBrukerCounter: Counter = Counter
        .build()
        .name("hm_soknad_mottak_sletting_fra_bruker")
        .help("Antall søknader slettet")
        .register(collectorRegistry)

    val ordrelinjeLagretCounter: Counter = Counter
        .build()
        .name("hm_oebs_ordrelinje_lagret")
        .help("Antall OEBS-ordrelinjer lagret")
        .register(collectorRegistry)

    val vedtaksresultatLagretCounter: Counter = Counter
        .build()
        .name("hm_vedtaksresultat_lagret")
        .help("Antall vedtaksresultat fra Infotrygd lagret")
        .register(collectorRegistry)

    val ordrelinjeLagretOgSendtTilRapidCounter: Counter = Counter
        .build()
        .name("hm_oebs_ordrelinje_sendt_til_rapid")
        .help("Antall OEBS-ordrelinjer lagret og sendt til rapid")
        .register(collectorRegistry)

    val knytningMellomSøknadOgInfotrygdOpprettetCounter: Counter = Counter
        .build()
        .name("hm_knytning_mellom_infotrygd_og_soeknad_oppretta")
        .help("Antall søknader som er knyttet til en Infotrygd-sak")
        .register(collectorRegistry)

    val knytningMellomSøknadOgInfotrygdProblemCounter: Counter = Counter
        .build()
        .name("hm_knytning_mellom_infotrygd_og_soeknad_oppretta_problem")
        .help("Antall feil ved knytning mellom søknader og Infotrygd-sak")
        .register(collectorRegistry)
}
