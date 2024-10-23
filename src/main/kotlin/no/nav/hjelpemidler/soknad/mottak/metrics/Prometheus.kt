package no.nav.hjelpemidler.soknad.mottak.metrics

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry

object Prometheus {
    val registry: MeterRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)

    val søknadMedFullmaktCounter: Counter = registry.counter("hm_soknad_med_fullmakt")
    val søknadTilGodkjenningCounter: Counter = registry.counter("hm_soknad_til_godkjenning")
    val søknadGodkjentAvBrukerCounter: Counter = registry.counter("hm_soknad_godkjenning_av_bruker")
    val godkjenningsfristErUtløptCounter: Counter = registry.counter("hm_soknad_godkjenningsfrist_utlopt")
    val søknadSlettetAvBrukerCounter: Counter = registry.counter("hm_soknad_slettet_av_bruker")
    val vedtaksresultatInfotrygdLagretCounter: Counter = registry.counter("hm_vedtaksresultat_infotrygd_lagret")
    val vedtaksresultatHotsakLagretCounter: Counter = registry.counter("hm_vedtaksresultat_hotsak_lagret")
    val ordrelinjeLagretCounter: Counter = registry.counter("hm_oebs_ordrelinje_lagret")
    val ordrelinjeVideresendtCounter: Counter = registry.counter("hm_oebs_ordrelinje_videresendt")
    val sakstilknytningInfotrygdLagretCounter: Counter = registry.counter("hm_sakstilknytning_infotrygd_lagret")
    val sakstilknytningHotsakLagretCounter: Counter = registry.counter("hm_sakstilknytning_hotsak_lagret")
}
