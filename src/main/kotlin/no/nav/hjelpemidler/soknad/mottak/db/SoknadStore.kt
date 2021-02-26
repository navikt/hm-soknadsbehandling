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
    fun hentSoknaderTilGodkjenning(fnrBruker: String): List<SoknadMedStatus>
}

internal class SoknadStorePostgres(private val ds: DataSource) : SoknadStore {

    override fun hentSoknad(soknadsId: UUID): SoknadForBruker? {
        @Language("PostgreSQL") val statement =
            """SELECT SOKNADS_ID, STATUS, DATA, CREATED
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
                            )
                        )
                    }.asSingle
                )
            }
        }
    }

    override fun hentSoknaderTilGodkjenning(fnrBruker: String): List<SoknadMedStatus> {
        @Language("PostgreSQL") val statement =
            """SELECT SOKNADS_ID, CREATED, STATUS
                    FROM V1_SOKNAD 
                    WHERE FNR_BRUKER = ? AND STATUS = ? """

        return time("hent_soknader_til_godkjenning") {
            using(sessionOf(ds)) { session ->
                session.run(
                    queryOf(
                        statement,
                        fnrBruker,
                        Status.VENTER_GODKJENNING.name
                    ).map {
                        SoknadMedStatus(
                            soknadId = UUID.fromString(it.string("SOKNADS_ID")),
                            status = Status.valueOf(it.string("STATUS")),
                            datoOpprettet = it.sqlTimestamp("created"),
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
