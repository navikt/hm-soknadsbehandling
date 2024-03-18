package no.nav.hjelpemidler.soknad.mottak.metrics

import com.influxdb.client.InfluxDBClientFactory
import com.influxdb.client.domain.WritePrecision
import com.influxdb.client.write.Point
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.hjelpemidler.soknad.mottak.Configuration
import no.nav.hjelpemidler.soknad.mottak.client.PdlClient
import no.nav.hjelpemidler.soknad.mottak.metrics.kommune.KommuneDto
import no.nav.hjelpemidler.soknad.mottak.metrics.kommune.KommuneService
import java.time.Instant

internal class Metrics(
    messageContext: MessageContext,
    private val pdlClient: PdlClient,
    private val kommuneService: KommuneService = KommuneService(),
    config: Configuration.InfluxDb = Configuration.influxDb
) {

    private val client = InfluxDBClientFactory.createV1(
        "${config.host}:${config.port}",
        config.user,
        config.password.toCharArray(),
        config.name,
        null
    )
    private val writeApi = client.makeWriteApi()
    private val metricsProducer = MetricsProducer(messageContext)

    suspend fun digitalSoknad(brukersFnr: String, soknadId: String) {
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
                logg.warn(e) { "Feil under logging av statistikk 'digitalsøknad per kommune'. Søknad: $soknadId" }
            }
        }
    }

    suspend fun papirSoknad(brukersFnr: String) {
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
                logg.warn(e) { "Feil under logging av statistikk 'papirsøknad per kommune'." }
            }
        }
    }

    suspend fun resultatFraInfotrygd(
        brukersFnr: String,
        vedtaksresultat: String,
        soknadsType: String
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
                logg.warn(e) { "Feil under logging av statistikk 'resultat fra infotrygd'." }
            }
        }
    }

    private suspend fun kommunenrTilSted(kommunenr: String?): KommuneDto {
        val sted = kommuneService.kommunenrTilSted(kommunenr)
        return if (sted == null) {
            logg.warn { "Ingen resultat for kommunenr oppslag på kommunenr <$kommunenr>" }
            ukjentSted
        } else {
            sted
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
            writeApi.writePoint(point)
            metricsProducer.hendelseOpprettet(measurement, fields, tags)
        } catch (e: Exception) {
            logg.warn(e) { "Skriving av event til influx feilet" }
        }
    }
}

private val ukjentSted = KommuneDto("UKJENT", "UKJENT", "UKJENT")

private val logg = KotlinLogging.logger {}

private val DEFAULT_TAGS: Map<String, String> = mapOf(
    "application" to Configuration.appName,
    "cluster" to Configuration.cluster
)

private const val PREFIX = "hm-soknadsbehandling"
const val STED = "$PREFIX.sted"
const val VEDTAKSRESULTAT_INFOTRYGD = "$PREFIX.vedtaksresultat-infotrygd"
