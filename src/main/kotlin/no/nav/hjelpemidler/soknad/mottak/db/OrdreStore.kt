package no.nav.hjelpemidler.soknad.mottak.db

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.hjelpemidler.soknad.mottak.metrics.Prometheus
import no.nav.hjelpemidler.soknad.mottak.service.OrdrelinjeData
import org.postgresql.util.PGobject
import javax.sql.DataSource

internal interface OrdreStore {
    fun save(ordrelinje: OrdrelinjeData): Int
}

internal class OrdreStorePostgres(private val ds: DataSource) : OrdreStore {

    override fun save(ordrelinje: OrdrelinjeData): Int =
        time("insert_ordrelinje") {
            using(sessionOf(ds)) { session ->
                session.run(
                    queryOf(
                        "INSERT INTO V1_OEBS_DATA (SOKNADS_ID, FNR_BRUKER, SERVICEFORESPOERSEL, ORDRENR, ORDRELINJE, ARTIKKELNR, ANTALL, DATA) VALUES (?,?,?,?,?,?,?,?) ON CONFLICT DO NOTHING",
                        ordrelinje.soknadId,
                        ordrelinje.fnrBruker,
                        ordrelinje.serviceforespoersel,
                        ordrelinje.ordrenr,
                        ordrelinje.ordrelinje,
                        ordrelinje.artikkelnr,
                        ordrelinje.antall,
                        PGobject().apply {
                            type = "jsonb"
                            value = ordrelinjeToJsonString(ordrelinje.data)
                        },
                    ).asUpdate
                )
            }
        }

    private inline fun <T : Any?> time(queryName: String, function: () -> T) =
        Prometheus.dbTimer.labels(queryName).startTimer().let { timer ->
            function().also {
                timer.observeDuration()
            }
        }

    companion object {
        private val objectMapper = jacksonObjectMapper()
            .registerModule(JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
    }

    private fun ordrelinjeToJsonString(ordrelinje: JsonNode): String = objectMapper.writeValueAsString(ordrelinje)
}
