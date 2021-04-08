package no.nav.hjelpemidler.soknad.mottak.db

import com.fasterxml.jackson.databind.JsonNode
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.hjelpemidler.soknad.mottak.JacksonMapper
import no.nav.hjelpemidler.soknad.mottak.metrics.Prometheus
import no.nav.hjelpemidler.soknad.mottak.service.Status
import org.intellij.lang.annotations.Language
import java.util.Date
import java.util.UUID
import javax.sql.DataSource

internal interface SøknadStoreFormidler {
    fun hentSøknaderForFormidler(fnrFormidler: String, uker: Int): List<SoknadForFormidler>
}

internal class SøknadStoreFormidlerPostgres(private val ds: DataSource) : SøknadStoreFormidler {

    override fun hentSøknaderForFormidler(fnrFormidler: String, uker: Int): List<SoknadForFormidler> {
        @Language("PostgreSQL") val statement =
            """
                SELECT soknad.SOKNADS_ID, soknad.CREATED, soknad.UPDATED, soknad.DATA, soknad.FNR_BRUKER, status.STATUS, 
                (CASE WHEN EXISTS (
                    SELECT 1 FROM V1_STATUS WHERE SOKNADS_ID = soknad.SOKNADS_ID AND STATUS IN  ('GODKJENT_MED_FULLMAKT')
                ) THEN true ELSE false END) as fullmakt
                FROM V1_SOKNAD AS soknad
                LEFT JOIN V1_STATUS AS status
                ON status.ID = (
                    SELECT MAX(ID) FROM V1_STATUS WHERE SOKNADS_ID = soknad.SOKNADS_ID
                )
                WHERE soknad.FNR_INNSENDER = ?
                AND (soknad.UPDATED + interval '$uker week') > now()
                ORDER BY soknad.UPDATED DESC
            """

        return time("hent_soknader_for_formidler") {
            using(sessionOf(ds)) { session ->
                session.run(
                    queryOf(
                        statement,
                        fnrFormidler,
                    ).map {
                        val status = Status.valueOf(it.string("STATUS"))
                        if (status.isSlettetEllerUtløpt()) {
                            SoknadForFormidler.newSøknadUtenBrukersNavn(
                                soknadId = UUID.fromString(it.string("SOKNADS_ID")),
                                status = Status.valueOf(it.string("STATUS")),
                                fullmakt = it.boolean("fullmakt"),
                                datoOpprettet = it.sqlTimestamp("created"),
                                datoOppdatert = when {
                                    it.sqlTimestampOrNull("updated") != null -> it.sqlTimestamp("updated")
                                    else -> it.sqlTimestamp("created")
                                },
                                fnrBruker = it.string("FNR_BRUKER")
                            )
                        } else {
                            SoknadForFormidler.newSøknadMedBrukersNavn(
                                soknadId = UUID.fromString(it.string("SOKNADS_ID")),
                                status = Status.valueOf(it.string("STATUS")),
                                fullmakt = it.boolean("fullmakt"),
                                datoOpprettet = it.sqlTimestamp("created"),
                                datoOppdatert = when {
                                    it.sqlTimestampOrNull("updated") != null -> it.sqlTimestamp("updated")
                                    else -> it.sqlTimestamp("created")
                                },
                                fnrBruker = it.string("FNR_BRUKER"),
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
}

private inline fun <T : Any?> time(queryName: String, function: () -> T) =
    Prometheus.dbTimer.labels(queryName).startTimer().let { timer ->
        function().also {
            timer.observeDuration()
        }
    }

class SoknadForFormidler private constructor(
    val søknadId: UUID,
    val datoOpprettet: Date,
    var datoOppdatert: Date,
    val status: Status,
    val fullmakt: Boolean,
    val fnrBruker: String,
    val navnBruker: String?
) {
    companion object {
        fun newSøknadUtenBrukersNavn(soknadId: UUID, datoOpprettet: Date, datoOppdatert: Date, status: Status, fullmakt: Boolean, fnrBruker: String) =
            SoknadForFormidler(soknadId, datoOpprettet, datoOppdatert, status, fullmakt, fnrBruker, null)

        fun newSøknadMedBrukersNavn(soknadId: UUID, datoOpprettet: Date, datoOppdatert: Date, status: Status, fullmakt: Boolean, fnrBruker: String, søknad: JsonNode) =
            SoknadForFormidler(soknadId, datoOpprettet, datoOppdatert, status, fullmakt, fnrBruker, brukersNavn(søknad))
    }
}

private fun brukersNavn(soknad: JsonNode): String {
    val brukerNode = soknad["soknad"]["bruker"]
    return "${brukerNode["fornavn"].textValue()} ${brukerNode["etternavn"].textValue()}"
}
