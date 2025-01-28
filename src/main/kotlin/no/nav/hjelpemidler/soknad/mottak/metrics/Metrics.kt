package no.nav.hjelpemidler.soknad.mottak.metrics

import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.hjelpemidler.metrics.createMetricsProducer
import no.nav.hjelpemidler.soknad.mottak.client.PdlClient
import no.nav.hjelpemidler.soknad.mottak.metrics.kommune.KommuneDto
import no.nav.hjelpemidler.soknad.mottak.metrics.kommune.KommuneService
import java.util.UUID

class Metrics(
    messageContext: MessageContext,
    private val pdlClient: PdlClient,
    private val kommuneService: KommuneService = KommuneService(),
) {
    private val metricsProducer = createMetricsProducer()
    private val kafkaMetricsProducer = MetricsProducer(messageContext)

    suspend fun digitalSøknad(brukersFnr: String, soknadId: UUID) {
        try {
            val kommunenr = pdlClient.hentKommunenr(brukersFnr)
            val sted = kommunenrTilSted(kommunenr)
            writeEvent(
                STED,
                mapOf("counter-digital" to 1L),
                mapOf("kommune" to sted.kommunenavn, "fylke" to sted.fylkenavn)
            )
        } catch (e: Exception) {
            log.warn(e) { "Feil under logging av statistikk 'digital søknad per kommune', søknadId: $soknadId" }
        }
    }

    suspend fun papirsøknad(brukersFnr: String) {
        try {
            val kommunenr = pdlClient.hentKommunenr(brukersFnr)
            val sted = kommunenrTilSted(kommunenr)
            writeEvent(
                STED,
                mapOf("counter-papir" to 1L),
                mapOf("kommune" to sted.kommunenavn, "fylke" to sted.fylkenavn)
            )
        } catch (e: Exception) {
            log.warn(e) { "Feil under logging av statistikk 'papirsøknad per kommune'" }
        }
    }

    suspend fun resultatFraInfotrygd(
        brukersFnr: String,
        vedtaksresultat: String,
        soknadsType: String,
    ) {
        try {
            val kommunenr = pdlClient.hentKommunenr(brukersFnr)
            val sted = kommunenrTilSted(kommunenr)
            writeEvent(
                "$VEDTAKSRESULTAT_INFOTRYGD.$soknadsType",
                mapOf("counter" to 1L),
                mapOf(
                    "vedtaksresultat" to vedtaksresultat,
                    "kommune" to sted.kommunenavn,
                    "fylke" to sted.fylkenavn
                )
            )
        } catch (e: Exception) {
            log.warn(e) { "Feil under logging av statistikk 'resultat fra infotrygd'." }
        }
    }

    private suspend fun kommunenrTilSted(kommunenr: String?): KommuneDto {
        val sted = kommuneService.kommunenrTilSted(kommunenr)
        return if (sted == null) {
            log.warn { "Ingen resultat for kommunenr oppslag på kommunenr: $kommunenr" }
            ukjentSted
        } else {
            sted
        }
    }

    private suspend fun writeEvent(measurement: String, fields: Map<String, Any>, tags: Map<String, String>) =
        try {
            metricsProducer.writeEvent(measurement, fields, tags)
            kafkaMetricsProducer.hendelseOpprettet(measurement, fields, tags)
        } catch (e: Exception) {
            log.warn(e) { "Publisering av metrikk feilet" }
        }
}

private val log = KotlinLogging.logger {}
private val ukjentSted = KommuneDto("UKJENT", "UKJENT", "UKJENT")

private const val PREFIX = "hm-soknadsbehandling"
private const val STED = "$PREFIX.sted"
private const val VEDTAKSRESULTAT_INFOTRYGD = "$PREFIX.vedtaksresultat-infotrygd"
