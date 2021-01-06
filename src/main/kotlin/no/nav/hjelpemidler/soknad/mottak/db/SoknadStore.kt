package no.nav.hjelpemidler.soknad.mottak.db

import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import mu.KotlinLogging
import no.nav.hjelpemidler.soknad.mottak.metrics.Prometheus
import no.nav.hjelpemidler.soknad.mottak.service.JournalPost
import no.nav.hjelpemidler.soknad.mottak.service.SoknadData
import no.nav.hjelpemidler.soknad.mottak.service.SoknadJournalpostMapping
import org.intellij.lang.annotations.Language
import org.postgresql.util.PGobject
import javax.sql.DataSource

internal interface SoknadStore {
    fun save(journalPost: JournalPost): Int
    fun save(soknadData: SoknadData): Int
    fun save(søknadJournalpostMapping: SoknadJournalpostMapping): Int
    fun findFagsakId(søknadsId: String): String?
    fun findJournalpostId(søknadsId: String): String?
}

internal class SoknadStorePostgres(private val ds: DataSource) : SoknadStore {
    companion object {
        private const val forwardLockKey = "soknad-forwarder"
        private val logger = KotlinLogging.logger {}
    }

    override fun save(journalPost: JournalPost): Int =
        time("insert_journal_post") {
            using(sessionOf(ds)) { session ->
                //language=PostgreSQL
                session.run(
                    queryOf(
                        """ INSERT INTO V1_JOURNAL_POST (FNR, AKTOER_ID, JOURNALPOST_ID,FAGSAK_ID,BEHANDLENDE_ENHET, DATO_REGISTRERT, HENVENDELSES_TYPE )
                            VALUES (:naturligIdent,:aktorId,:journalpostId,:fagsakId,:behandlendeEnhet,:datoRegistrert, :henvendelsesType)
                            ON CONFLICT DO NOTHING 
                        """.trimMargin(),
                        mapOf(
                            "naturligIdent" to journalPost.naturligIdent,
                            "aktorId" to journalPost.aktørId,
                            "journalpostId" to journalPost.journalpostId,
                            "fagsakId" to journalPost.fagsakId,
                            "behandlendeEnhet" to journalPost.behandlendeEnhet,
                            "datoRegistrert" to journalPost.datoRegistrert,
                            "henvendelsesType" to journalPost.henvendelsestype,
                        )
                    ).asUpdate
                )
            }
        }

    override fun save(soknadData: SoknadData): Int =
        time("insert_soknad") {
            using(sessionOf(ds)) { session ->
                session.run(
                    queryOf(
                        "INSERT INTO V2_SOKNAD (FNR, SOKNADS_ID, DATA ) VALUES (?,?,?) ON CONFLICT DO NOTHING",
                        soknadData.fnr,
                        soknadData.søknadsId,
                        PGobject().apply {
                            type = "jsonb"
                            value = soknadData.soknad
                        }
                    ).asUpdate
                )
            }
        }

    override fun save(søknadJournalpostMapping: SoknadJournalpostMapping): Int {
        return time("insert_soknad_journalpost_mapping") {
            using(sessionOf(ds)) { session ->
                session.run(
                    queryOf(
                        """INSERT INTO V1_SOKNAD_JOURNALPOST_MAPPING (SOKNADS_ID,JOURNALPOST_ID) VALUES (?, ?)  ON CONFLICT DO NOTHING""",
                        søknadJournalpostMapping.søknadsId,
                        søknadJournalpostMapping.journalpostId
                    ).asUpdate
                )
            }
        }
    }

    override fun findFagsakId(søknadsId: String): String? {
        @Language("PostgreSQL") val statement =
            """SELECT FAGSAK_ID FROM V1_JOURNAL_POST
               WHERE JOURNALPOST_ID = (
                SELECT JOURNALPOST_ID
                FROM V1_SOKNAD_JOURNALPOST_MAPPING
                WHERE SOKNADS_ID = ? )"""
        return time("find_fagsakid_by_soknadsid") {
            using(sessionOf(ds)) { session ->
                session.run(
                    queryOf(statement, søknadsId).map { it.string("FAGSAK_ID") }.asSingle
                )
            }
        }
    }

    override fun findJournalpostId(søknadsId: String): String? {
        @Language("PostgreSQL") val statement =
            """SELECT JOURNALPOST_ID FROM V1_JOURNAL_POST
               WHERE JOURNALPOST_ID = (
                SELECT JOURNALPOST_ID
                FROM V1_SOKNAD_JOURNALPOST_MAPPING
                WHERE SOKNADS_ID = ? )"""
        return time("find_journalpostid_by_soknadsid") {
            using(sessionOf(ds)) { session ->
                session.run(
                    queryOf(statement, søknadsId).map { it.string("JOURNALPOST_ID") }.asSingle
                )
            }
        }
    }

    private inline fun <T : Any?> time(queryName: String, function: () -> T) =
        Prometheus.dbTimer.labels(queryName).startTimer().let { timer ->
            function().also {
                timer.observeDuration()
            }
        }
}
