package no.nav.hjelpemidler.soknad.mottak.metrics

import com.influxdb.client.InfluxDBClientFactory
import com.influxdb.client.domain.WritePrecision
import com.influxdb.client.write.Point
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import no.nav.hjelpemidler.soknad.mottak.Configuration
import no.nav.hjelpemidler.soknad.mottak.client.PdlClient
import java.time.Instant

internal class InfluxMetrics(
    private val pdlClient: PdlClient,
    config: Configuration.InfluxDb = Configuration.influxDb
) {

    private val client = InfluxDBClientFactory.createV1(
        "${config.host}:${config.port}",
        config.user,
        config.password.toCharArray(),
        config.name,
        null
    )

    suspend fun digitalSoknad(brukersFnr: String, soknadId: String) {
        withContext(Dispatchers.IO) {
            try {
                val kommunenr = pdlClient.hentKommunenr(brukersFnr)
                val sted = Kommunenr.kommunenrTilSted(kommunenr) ?: ukjentSted
                writeEvent(
                    STED,
                    mapOf("counter-digital" to 1L),
                    mapOf("kommune" to sted.kommunenavn, "fylke" to sted.fylkesnavn)
                )
            } catch (e: Exception) {
                logg.warn(e) { "Feil under logging av statistikk 'digitalsøknad per kommune'. Søknad: $soknadId" }
            }
        }
    }

    suspend fun papirSoknad(brukersFnr: String) {
        withContext(Dispatchers.IO) {
            try {
                val kommunenr = pdlClient.hentKommunenr(brukersFnr)
                val sted = Kommunenr.kommunenrTilSted(kommunenr) ?: ukjentSted
                writeEvent(
                    STED,
                    mapOf("counter-papir" to 1L),
                    mapOf("kommune" to sted.kommunenavn, "fylke" to sted.fylkesnavn)
                )
            } catch (e: Exception) {
                logg.warn(e) { "Feil under logging av statistikk 'papirsøknad per kommune'." }
            }
        }
    }

    private fun writeEvent(measurement: String, fields: Map<String, Any>, tags: Map<String, String>) = runBlocking {
        try {
            val point = Point(measurement)
                .addTags(DEFAULT_TAGS)
                .addTags(tags)
                .addFields(fields)
                .time(Instant.now().toEpochMilli(), WritePrecision.MS)

            logg.info("Skriv point-objekt til Aiven: ${point.toLineProtocol()}")
            client.writeApi.writePoint(point)
        } catch (e: Exception) {
            logg.warn(e) { "Skriving av event til influx feilet" }
        }
    }
}

private val ukjentSted = Kommunenr.Sted("UKJENT", "UKJENT")

private val logg = KotlinLogging.logger {}

private val DEFAULT_TAGS: Map<String, String> = mapOf(
    "application" to Configuration.appName,
    "cluster" to Configuration.cluster,
)

private const val PREFIX = "hm-soknadsbehandling"
const val STED = "$PREFIX.sted"
