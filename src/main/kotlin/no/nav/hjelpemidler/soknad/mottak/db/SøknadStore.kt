package no.nav.hjelpemidler.soknad.mottak.db

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.hjelpemidler.soknad.mottak.JacksonMapper
import no.nav.hjelpemidler.soknad.mottak.metrics.Prometheus
import no.nav.hjelpemidler.soknad.mottak.service.SoknadData
import no.nav.hjelpemidler.soknad.mottak.service.SoknadMedStatus
import no.nav.hjelpemidler.soknad.mottak.service.Status
import no.nav.hjelpemidler.soknad.mottak.service.SøknadForBruker
import no.nav.hjelpemidler.soknad.mottak.service.UtgåttSøknad
import org.intellij.lang.annotations.Language
import org.postgresql.util.PGobject
import java.util.UUID
import javax.sql.DataSource

internal interface SøknadStore {
    fun save(soknadData: SoknadData): Int
    fun hentSoknad(soknadsId: UUID): SøknadForBruker?
    fun hentSoknaderForBruker(fnrBruker: String): List<SoknadMedStatus>
    fun hentSoknadData(soknadsId: UUID): SoknadData?
    fun oppdaterStatus(soknadsId: UUID, status: Status): Int
    fun oppdaterUtgåttSøknad(soknadsId: UUID): Int
    fun hentFnrForSoknad(soknadsId: UUID): String
    fun hentSoknaderTilGodkjenningEldreEnn(dager: Int): List<UtgåttSøknad>
    fun soknadFinnes(soknadsId: UUID): Boolean
}

internal class SøknadStorePostgres(private val ds: DataSource) : SøknadStore {

    override fun soknadFinnes(soknadsId: UUID): Boolean {
        @Language("PostgreSQL") val statement =
            """SELECT SOKNADS_ID
                    FROM V1_SOKNAD 
                    WHERE SOKNADS_ID = ?"""

        val uuid = time("soknad_eksisterer") {
            using(sessionOf(ds)) { session ->
                session.run(
                    queryOf(
                        statement,
                        soknadsId,
                    ).map {
                        UUID.fromString(it.string("SOKNADS_ID"))
                    }.asSingle
                )
            }
        }
        return uuid != null
    }

    override fun hentSoknad(soknadsId: UUID): SøknadForBruker? {
        @Language("PostgreSQL") val statement =
            """SELECT SOKNADS_ID, STATUS, DATA, CREATED, KOMMUNENAVN, FNR_BRUKER, UPDATED
                    FROM V1_SOKNAD 
                    WHERE SOKNADS_ID = ?"""

        return time("hent_soknad") {
            using(sessionOf(ds)) { session ->
                session.run(
                    queryOf(
                        statement,
                        soknadsId,
                    ).map {
                        val status = Status.valueOf(it.string("STATUS"))
                        if (status.isSlettetEllerUtløpt()) {
                            SøknadForBruker.newEmptySøknad(
                                søknadId = UUID.fromString(it.string("SOKNADS_ID")),
                                status = Status.valueOf(it.string("STATUS")),
                                datoOpprettet = it.sqlTimestamp("created"),
                                datoOppdatert = when {
                                    it.sqlTimestampOrNull("updated") != null -> it.sqlTimestamp("updated")
                                    else -> it.sqlTimestamp("created")
                                },
                                fnrBruker = it.string("FNR_BRUKER")
                            )
                        } else {
                            SøknadForBruker.new(
                                søknadId = UUID.fromString(it.string("SOKNADS_ID")),
                                status = Status.valueOf(it.string("STATUS")),
                                datoOpprettet = it.sqlTimestamp("created"),
                                datoOppdatert = when {
                                    it.sqlTimestampOrNull("updated") != null -> it.sqlTimestamp("updated")
                                    else -> it.sqlTimestamp("created")
                                },
                                søknad = JacksonMapper.objectMapper.readTree(
                                    it.stringOrNull("DATA") ?: "{}"
                                ),
                                kommunenavn = it.stringOrNull("KOMMUNENAVN"),
                                fnrBruker = it.string("FNR_BRUKER")
                            )
                        }
                    }.asSingle
                )
            }
        }
    }

    override fun hentSoknadData(soknadsId: UUID): SoknadData? {
        @Language("PostgreSQL") val statement =
            """SELECT SOKNADS_ID,FNR_BRUKER, FNR_INNSENDER, STATUS, DATA, KOMMUNENAVN
                    FROM V1_SOKNAD 
                    WHERE SOKNADS_ID = ?"""

        return time("hent_soknaddata") {
            using(sessionOf(ds)) { session ->
                session.run(
                    queryOf(
                        statement,
                        soknadsId,
                    ).map {
                        SoknadData(
                            fnrBruker = it.string("FNR_BRUKER"),
                            fnrInnsender = it.string("FNR_INNSENDER"),
                            soknadId = UUID.fromString(it.string("SOKNADS_ID")),
                            status = Status.valueOf(it.string("STATUS")),
                            soknad = JacksonMapper.objectMapper.readTree(
                                it.string("DATA")
                            ),
                            kommunenavn = it.stringOrNull("KOMMUNENAVN")
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

    override fun oppdaterUtgåttSøknad(soknadsId: UUID): Int =
        time("oppdater_utgaatt_soknad") {
            using(sessionOf(ds)) { session ->
                session.run(
                    queryOf(
                        "UPDATE V1_SOKNAD SET STATUS = ?, UPDATED = now(), DATA = NULL WHERE SOKNADS_ID = ? AND STATUS NOT LIKE ?",
                        Status.UTLØPT.name,
                        soknadsId,
                        Status.UTLØPT.name
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
                        val status = Status.valueOf(it.string("STATUS"))
                        if (status.isSlettetEllerUtløpt()) {
                            SoknadMedStatus.newSøknadUtenFormidlernavn(
                                soknadId = UUID.fromString(it.string("SOKNADS_ID")),
                                status = Status.valueOf(it.string("STATUS")),
                                datoOpprettet = it.sqlTimestamp("created"),
                                datoOppdatert = when {
                                    it.sqlTimestampOrNull("updated") != null -> it.sqlTimestamp("updated")
                                    else -> it.sqlTimestamp("created")
                                }
                            )
                        } else {
                            SoknadMedStatus.newSøknadMedFormidlernavn(
                                soknadId = UUID.fromString(it.string("SOKNADS_ID")),
                                status = Status.valueOf(it.string("STATUS")),
                                datoOpprettet = it.sqlTimestamp("created"),
                                datoOppdatert = when {
                                    it.sqlTimestampOrNull("updated") != null -> it.sqlTimestamp("updated")
                                    else -> it.sqlTimestamp("created")
                                },
                                søknad = JacksonMapper.objectMapper.readTree(
                                    it.string("DATA")
                                )
                            )
                        }
                    }.asList
                )
            }
        }
    }

    override fun hentSoknaderTilGodkjenningEldreEnn(dager: Int): List<UtgåttSøknad> {
        @Language("PostgreSQL") val statement =
            """SELECT SOKNADS_ID, STATUS, FNR_BRUKER
                    FROM V1_SOKNAD 
                    WHERE STATUS = ? AND (CREATED + interval '$dager day') < now()
                    ORDER BY CREATED DESC """

        return time("utgåtte_søknader") {
            using(sessionOf(ds)) { session ->
                session.run(
                    queryOf(
                        statement,
                        Status.VENTER_GODKJENNING.name,
                    ).map {
                        UtgåttSøknad(
                            søknadId = UUID.fromString(it.string("SOKNADS_ID")),
                            status = Status.valueOf(it.string("STATUS")),
                            fnrBruker = it.string("FNR_BRUKER")
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
                        "INSERT INTO V1_SOKNAD (SOKNADS_ID,FNR_BRUKER, FNR_INNSENDER, STATUS, DATA, KOMMUNENAVN ) VALUES (?,?,?,?,?,?) ON CONFLICT DO NOTHING",
                        soknadData.soknadId,
                        soknadData.fnrBruker,
                        soknadData.fnrInnsender,
                        soknadData.status.name,
                        PGobject().apply {
                            type = "jsonb"
                            value = soknadToJsonString(soknadData.soknad)
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

    companion object {
        private val objectMapper = jacksonObjectMapper()
            .registerModule(JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
    }

    private fun soknadToJsonString(soknad: JsonNode): String = objectMapper.writeValueAsString(soknad)
}
