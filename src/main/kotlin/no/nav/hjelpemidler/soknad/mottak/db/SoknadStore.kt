package no.nav.hjelpemidler.soknad.mottak.db

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.util.JSONPObject
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.hjelpemidler.soknad.mottak.metrics.Prometheus
import no.nav.hjelpemidler.soknad.mottak.service.SoknadData
import no.nav.hjelpemidler.soknad.mottak.service.SoknadForBruker
import no.nav.hjelpemidler.soknad.mottak.service.Status
import org.intellij.lang.annotations.Language
import org.postgresql.util.PGobject
import java.util.*
import javax.sql.DataSource

internal interface SoknadStore {
    fun save(soknadData: SoknadData): Int
    fun hentSoknad(søknadsId: String): SoknadForBruker?
}


internal class SoknadStorePostgres(private val ds: DataSource) : SoknadStore {

    companion object {
        private val objectMapper = jacksonObjectMapper()
                .registerModule(JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
    }


    override fun hentSoknad(soknadsId: String): SoknadForBruker? {
        @Language("PostgreSQL") val statement =
                """SELECT SOKNADS_ID,FNR_BRUKER, FNR_INNSENDER, STATUS, DATA 
                    FROM V1_SOKNAD 
                    WHERE SOKNADS_ID = ?"""

        return time("hent_soknad") {
            using(sessionOf(ds)) { session ->
                session.run(
                        queryOf(
                                statement,
                                soknadsId,
                        ).map {
                            SoknadForBruker(
                                    soknadId = UUID.fromString(it.string("SOKNADS_ID")),
                                    fnrBruker = it.string("FNR_BRUKER"),
                                    status = Status.valueOf(it.string("STATUS")),
                                    datoOpprettet = it.sqlTimestamp("created"),
                                    soknad = objectMapper.readTree(
                                            it.string("DATA")
                                    )
                            )
                        }.asSingle
                )
            }
        }

    }

    override fun save(soknadData: SoknadData): Int =
            time("insert_soknad") {
                using(sessionOf(ds)) { session ->
                    session.run(
                            queryOf(
                                    "INSERT INTO V1_SOKNAD (SOKNADS_ID,FNR_BRUKER, FNR_INNSENDER, STATUS, DATA ) VALUES (?,?,?,?,?) ON CONFLICT DO NOTHING",
                                    soknadData.soknadId,
                                    soknadData.fnrBruker,
                                    soknadData.fnrInnsender,
                                    soknadData.status.name,
                                    PGobject().apply {
                                        type = "jsonb"
                                        value = soknadData.soknadJson
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
}

