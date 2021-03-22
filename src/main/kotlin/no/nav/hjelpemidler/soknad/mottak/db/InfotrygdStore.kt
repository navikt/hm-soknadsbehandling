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
import org.intellij.lang.annotations.Language
import java.time.LocalDate
import java.util.*
import javax.sql.DataSource

internal interface InfotrygdStore {
    fun hentSoknadIdFraResultat(fnrBruker: String, saksblokkOgSaksnummer: String, vedtaksdato: LocalDate): UUID
}

internal class InfotrygdStorePostgres(private val ds: DataSource) : InfotrygdStore {

    override fun hentSoknadIdFraResultat(fnrBruker: String, saksblokkOgSaksnummer: String, vedtaksdato: LocalDate): UUID {
        fun getSaksblokk(saksblokkOgSaksnummer: String): Char {
            return saksblokkOgSaksnummer.first()
        }
        fun getSaksnr(saksblokkOgSaksnummer: String): String {
            return saksblokkOgSaksnummer.takeLast(2)
        }

        @Language("PostgreSQL") val statement =
                """SELECT SOKNADS_ID
                    FROM V1_INFOTRYGD_DATA 
                    WHERE FNR_BRUKER = ?
                    AND SAKSBLOKK = ?
                    AND SAKSNR = ?
                    AND VEDTAKSDATO = ?
        """
        val uuids: List<UUID> = time("hent_soknadid_fra_resultat") {
            using(sessionOf(ds)) { session ->
                session.run(
                    queryOf(
                            statement,
                            fnrBruker,
                            getSaksblokk(saksblokkOgSaksnummer),
                            getSaksnr(saksblokkOgSaksnummer),
                            vedtaksdato,
                    ).map {
                        UUID.fromString(it.string("SOKNADS_ID"))
                    }.asList
                )
            }

        }
        if (uuids.count() > 1) {
            throw RuntimeException("Fleire søknadar med likt fnr, saksblokk og vedtaksdato!")
        } else if (uuids.count() == 0 ) {
            throw RuntimeException("Ingen søknadar med korrekt fnr, saksblokk og vedtaksdato!")
        }
        return uuids[0]
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
