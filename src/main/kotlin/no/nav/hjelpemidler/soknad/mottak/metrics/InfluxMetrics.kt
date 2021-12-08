package no.nav.hjelpemidler.soknad.mottak.metrics

import com.influxdb.client.InfluxDBClientFactory
import com.influxdb.client.domain.WritePrecision
import com.influxdb.client.write.Point
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import no.nav.hjelpemidler.soknad.mottak.Configuration
import java.time.Instant

internal class InfluxMetrics(config: Configuration.InfluxDb = Configuration.influxDb) {

    private val client = InfluxDBClientFactory.createV1(
        "${config.host}:${config.port}",
        config.user,
        config.password.toCharArray(),
        config.name,
        null
    )

    private val ukjentSted = Kommunenr.Sted("UKJENT", "UKJENT")

    fun digitalSoknad(kommunenr: String?) {
        val sted = Kommunenr.kommunenrTilSted(kommunenr) ?: ukjentSted
        writeEvent(
            STED,
            mapOf("counter-digital" to 1L),
            mapOf("kommune" to sted.kommunenavn, "fylke" to sted.fylkesnavn)
        )
    }

    fun papirSoknad(kommuneNr: String?) {
        val sted = Kommunenr.kommunenrTilSted(kommuneNr) ?: ukjentSted
        writeEvent(STED, mapOf("counter-papir" to 1L), mapOf("kommune" to sted.kommunenavn, "fylke" to sted.fylkesnavn))
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

private val logg = KotlinLogging.logger {}

private val DEFAULT_TAGS: Map<String, String> = mapOf(
    "application" to Configuration.appName,
    "cluster" to Configuration.cluster,
)

private const val PREFIX = "hm-soknadsbehandling"
const val STED = "$PREFIX.sted"
