package no.nav.hjelpemidler.soknad.mottak.metrics

import com.influxdb.client.InfluxDBClientFactory
import com.influxdb.client.domain.WritePrecision
import com.influxdb.client.write.Point
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.hjelpemidler.configuration.InfluxDBEnvironmentVariable
import no.nav.hjelpemidler.configuration.NaisEnvironmentVariable
import no.nav.hjelpemidler.soknad.mottak.client.PdlClient
import no.nav.hjelpemidler.soknad.mottak.metrics.kommune.KommuneDto
import no.nav.hjelpemidler.soknad.mottak.metrics.kommune.KommuneService
import java.time.Instant
import java.util.UUID

class Metrics(
    messageContext: MessageContext,
    private val pdlClient: PdlClient,
    private val kommuneService: KommuneService = KommuneService(),
) {

    private val client = InfluxDBClientFactory.createV1(
        "${InfluxDBEnvironmentVariable.INFLUX_HOST}:${InfluxDBEnvironmentVariable.INFLUX_PORT}",
        InfluxDBEnvironmentVariable.INFLUX_USER,
        InfluxDBEnvironmentVariable.INFLUX_PASSWORD.toCharArray(),
        InfluxDBEnvironmentVariable.INFLUX_DATABASE_NAME,
        null
    )
    private val writeApi = client.makeWriteApi()
    private val metricsProducer = MetricsProducer(messageContext)

    suspend fun digitalSøknad(brukersFnr: String, soknadId: UUID) {
        withContext(Dispatchers.IO) {
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
    }

    suspend fun papirsøknad(brukersFnr: String) {
        withContext(Dispatchers.IO) {
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
    }

    suspend fun resultatFraInfotrygd(
        brukersFnr: String,
        vedtaksresultat: String,
        soknadsType: String,
    ) {
        withContext(Dispatchers.IO) {
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

    private fun writeEvent(measurement: String, fields: Map<String, Any>, tags: Map<String, String>) =
        runBlocking(Dispatchers.IO) {
            try {
                val point = Point(measurement)
                    .addTags(DEFAULT_TAGS)
                    .addTags(tags)
                    .addFields(fields)
                    .time(Instant.now().toEpochMilli(), WritePrecision.MS)

                log.debug {
                    "Sender metrikk: ${point.toLineProtocol()}"
                }
                writeApi.writePoint(point)
                metricsProducer.hendelseOpprettet(measurement, fields, tags)
            } catch (e: Exception) {
                log.warn(e) { "Sending av metrikk feilet" }
            }
        }
}

private val ukjentSted = KommuneDto("UKJENT", "UKJENT", "UKJENT")

private val log = KotlinLogging.logger {}

private val DEFAULT_TAGS: Map<String, String> = mapOf(
    "application" to NaisEnvironmentVariable.NAIS_APP_NAME,
    "cluster" to NaisEnvironmentVariable.NAIS_CLUSTER_NAME,
)

private const val PREFIX = "hm-soknadsbehandling"
const val STED = "$PREFIX.sted"
const val VEDTAKSRESULTAT_INFOTRYGD = "$PREFIX.vedtaksresultat-infotrygd"
