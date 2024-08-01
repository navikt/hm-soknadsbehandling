package no.nav.hjelpemidler.soknad.mottak.metrics

import io.prometheus.client.CollectorRegistry
import io.prometheus.client.Counter

object Prometheus {
    private val collectorRegistry: CollectorRegistry = CollectorRegistry.defaultRegistry

    val søknadMedFullmaktCounter: Counter = Counter
        .build()
        .name("hm_soknad_med_fullmakt")
        .help("Antall søknader med fullmakt")
        .register(collectorRegistry)

    val søknadTilGodkjenningCounter: Counter = Counter
        .build()
        .name("hm_soknad_til_godkjenning")
        .help("Antall søknader til godkjenning")
        .register(collectorRegistry)

    val søknadGodkjentAvBrukerCounter: Counter = Counter
        .build()
        .name("hm_soknad_godkjenning_av_bruker")
        .help("Antall søknader godkjent")
        .register(collectorRegistry)

    val godkjenningsfristErUtløptCounter: Counter = Counter
        .build()
        .name("hm_soknad_godkjenningsfrist_utlopt")
        .help("Antall søknader der godkjenningsfrist er utløpt")
        .register(collectorRegistry)

    val søknadSlettetAvBrukerCounter: Counter = Counter
        .build()
        .name("hm_soknad_slettet_av_bruker")
        .help("Antall søknader slettet av bruker")
        .register(collectorRegistry)

    val vedtaksresultatInfotrygdLagretCounter: Counter = Counter
        .build()
        .name("hm_vedtaksresultat_infotrygd_lagret")
        .help("Antall Infotrygd-vedtak lagret")
        .register(collectorRegistry)

    val vedtaksresultatHotsakLagretCounter: Counter = Counter
        .build()
        .name("hm_vedtaksresultat_hotsak_lagret")
        .help("Antall Hotsak-vedtak lagret")
        .register(collectorRegistry)

    val ordrelinjeLagretCounter: Counter = Counter
        .build()
        .name("hm_oebs_ordrelinje_lagret")
        .help("Antall OEBS-ordrelinjer lagret")
        .register(collectorRegistry)

    val ordrelinjeVideresendtCounter: Counter = Counter
        .build()
        .name("hm_oebs_ordrelinje_videresendt")
        .help("Antall OEBS-ordrelinjer videresendt")
        .register(collectorRegistry)

    val sakstilknytningInfotrygdLagretCounter: Counter = Counter
        .build()
        .name("hm_sakstilknytning_infotrygd_lagret")
        .help("Antall Infotrygd-saker knyttet til søknad")
        .register(collectorRegistry)

    val sakstilknytningHotsakLagretCounter: Counter = Counter
        .build()
        .name("hm_sakstilknytning_hotsak_lagret")
        .help("Antall Hotsak-saker knyttet til søknad")
        .register(collectorRegistry)
}
