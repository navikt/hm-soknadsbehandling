package no.nav.hjelpemidler.soknad.mottak.db

import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.hjelpemidler.soknad.mottak.JacksonMapper
import no.nav.hjelpemidler.soknad.mottak.metrics.Prometheus
import no.nav.hjelpemidler.soknad.mottak.service.SoknadData
import no.nav.hjelpemidler.soknad.mottak.service.SoknadForBruker
import no.nav.hjelpemidler.soknad.mottak.service.SoknadMedStatus
import no.nav.hjelpemidler.soknad.mottak.service.Status
import org.intellij.lang.annotations.Language
import org.postgresql.util.PGobject
import java.util.UUID
import javax.sql.DataSource

internal interface SoknadStore {
    fun save(soknadData: SoknadData): Int
    fun hentSoknad(soknadsId: UUID): SoknadForBruker?
    fun hentSoknaderForBruker(fnrBruker: String): List<SoknadMedStatus>
    fun oppdaterStatus(soknadsId: UUID, status: Status): Int
    fun hentFnrForSoknad(soknadsId: UUID): String
}

internal class SoknadStorePostgres(private val ds: DataSource) : SoknadStore {

    override fun hentSoknad(soknadsId: UUID): SoknadForBruker? {
        @Language("PostgreSQL") val statement =
            """SELECT SOKNADS_ID, STATUS, DATA, CREATED, KOMMUNENAVN
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
                            status = Status.valueOf(it.string("STATUS")),
                            datoOpprettet = it.sqlTimestamp("created"),
                            soknad = JacksonMapper.objectMapper.readTree(
                                it.string("DATA")
                            ),
                            kommunenavn = it.string("KOMMUNENAVN")
                        )
                    }.asSingle
                )
            }
        }
    }

    override fun hentFnrForSoknad(soknadsId: UUID): String {
        @Language("PostgreSQL") val statement =
            """SELECT FNR_BRUKER
                    FROM V1_SOKNAD 
                    WHERE SOKNADS_ID = ?"""

        val fnrBruker =
            time("hent_soknad") {
                using(sessionOf(ds)) { session ->
                    session.run(
                        queryOf(
                            statement,
                            soknadsId,
                        ).map {
                            it.string("FNR_BRUKER")
                        }.asSingle
                    )
                }
            }

        if (fnrBruker == null) {
            throw RuntimeException("No søknad with FNR found for soknadsId $soknadsId")
        } else {
            return fnrBruker
        }
    }

    override fun oppdaterStatus(soknadsId: UUID, status: Status): Int =
        time("oppdater_status") {
            using(sessionOf(ds)) { session ->
                session.run(
                    queryOf(
                        "UPDATE V1_SOKNAD SET STATUS = ?, UPDATED = now() WHERE SOKNADS_ID = ? AND STATUS NOT LIKE ?",
                        status.name,
                        soknadsId,
                        status.name
                    ).asUpdate
                )
            }
        }

    override fun hentSoknaderForBruker(fnrBruker: String): List<SoknadMedStatus> {
        @Language("PostgreSQL") val statement =
            """SELECT SOKNADS_ID, CREATED, UPDATED, STATUS, DATA
                    FROM V1_SOKNAD 
                    WHERE FNR_BRUKER = ? 
                    ORDER BY CREATED DESC """

        return time("hent_soknader_for_bruker") {
            using(sessionOf(ds)) { session ->
                session.run(
                    queryOf(
                        statement,
                        fnrBruker,
                    ).map {
                        SoknadMedStatus(
                            soknadId = UUID.fromString(it.string("SOKNADS_ID")),
                            status = Status.valueOf(it.string("STATUS")),
                            datoOpprettet = it.sqlTimestamp("created"),
                            datoOppdatert = when {
                                it.sqlTimestampOrNull("updated") != null -> it.sqlTimestamp("updated")
                                else -> it.sqlTimestamp("created")
                            },
                            soknad = JacksonMapper.objectMapper.readTree(
                                it.string("DATA")
                            )
                        )
                    }.asList
                )
            }
        }
    }

    override fun save(soknadData: SoknadData): Int =
        time("insert_soknad") {
            using(sessionOf(ds)) { session ->
                session.run(
                    queryOf(
                        "INSERT INTO V1_SOKNAD (SOKNADS_ID,FNR_BRUKER, FNR_INNSENDER, STATUS, DATA, KOMMUNENAVN ) VALUES (?,?,?,?,?, ?) ON CONFLICT DO NOTHING",
                        soknadData.soknadId,
                        soknadData.fnrBruker,
                        soknadData.fnrInnsender,
                        soknadData.status.name,
                        PGobject().apply {
                            type = "jsonb"
                            value = soknadData.soknadJson
                        },
                        soknadData.kommunenavn,
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
