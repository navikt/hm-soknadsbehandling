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
            """SELECT SOKNADS_ID, CREATED, UPDATED, STATUS, DATA, FNR_BRUKER
                    FROM V1_SOKNAD 
                    WHERE FNR_INNSENDER = ? 
                    AND (UPDATED + interval '$uker week') > now()
                    ORDER BY UPDATED DESC """

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
    val fnrBruker: String,
    val navnBruker: String?
) {
    companion object {
        fun newSøknadUtenBrukersNavn(soknadId: UUID, datoOpprettet: Date, datoOppdatert: Date, status: Status, fnrBruker: String) =
            SoknadForFormidler(soknadId, datoOpprettet, datoOppdatert, status, fnrBruker, null)

        fun newSøknadMedBrukersNavn(soknadId: UUID, datoOpprettet: Date, datoOppdatert: Date, status: Status, fnrBruker: String, søknad: JsonNode) =
            SoknadForFormidler(soknadId, datoOpprettet, datoOppdatert, status, fnrBruker, brukersNavn(søknad))
    }
}

private fun brukersNavn(soknad: JsonNode): String {
    val brukerNode = soknad["soknad"]["bruker"]
    return "${brukerNode["fornavn"].textValue()} ${brukerNode["etternavn"].textValue()}"
}
