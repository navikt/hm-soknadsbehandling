package no.nav.hjelpemidler.soknad.mottak.db

import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.hjelpemidler.soknad.mottak.metrics.Prometheus
import no.nav.hjelpemidler.soknad.mottak.service.VedtaksresultatData
import java.time.LocalDate
import java.util.UUID
import javax.sql.DataSource

internal interface InfotrygdStore {
    fun lagKnytningMellomFagsakOgSøknad(søknadId: UUID, fnrBruker: String, fagsakId: String): Int
    fun lagreVedtaksresultat(
        søknadId: UUID,
        fnrBruker: String,
        fagsakId: String,
        resultat: String,
        vedtaksdato: LocalDate
    ): Int

    fun hentSøknadIdFraResultat(fnrBruker: String, saksblokkOgSaksnummer: String, vedtaksdato: LocalDate): UUID
    fun hentVedtaksresultatForSøknad(søknadId: UUID): VedtaksresultatData?
}

internal class InfotrygdStorePostgres(private val ds: DataSource) : InfotrygdStore {

    // EndeligJournalført frå Joark vil opprette linja, og denne blir berika seinare av Infotrygd med resultat og vedtaksdato
    override fun lagKnytningMellomFagsakOgSøknad(søknadId: UUID, fnrBruker: String, fagsakId: String): Int =
        time("insert_knytning_mellom_søknad_og_fagsak") {
            val trygdekontorNr = fagsakId.take(4)
            val saksblokkOgSaksnummer = fagsakId.takeLast(3)

            using(sessionOf(ds)) { session ->
                session.run(
                    queryOf(
                        "INSERT INTO V1_INFOTRYGD_DATA (SOKNADS_ID, FNR_BRUKER, TRYGDEKONTORNR, SAKSBLOKK, SAKSNR, RESULTAT, VEDTAKSDATO ) VALUES (?,?,?,?,?,?,?) ON CONFLICT DO NOTHING",
                        søknadId,
                        fnrBruker,
                        trygdekontorNr,
                        getSaksblokk(saksblokkOgSaksnummer),
                        getSaksnr(saksblokkOgSaksnummer),
                        null,
                        null,
                    ).asUpdate
                )
            }
        }

    // Vedtaksresultat vil bli gitt av Infotrygd-poller som har oversikt over søknadId, fnr og fagsakId
    override fun lagreVedtaksresultat(
        søknadId: UUID,
        fnrBruker: String,
        fagsakId: String,
        resultat: String,
        vedtaksdato: LocalDate
    ): Int =
        time("oppdater_vedtaksresultat") {
            using(sessionOf(ds)) { session ->
                session.run(
                    queryOf(
                        "UPDATE V1_INFOTRYGD_DATA SET RESULTAT = ?, VEDTAKSDATO = ? WHERE SOKNADS_ID = ?",
                        resultat,
                        vedtaksdato,
                        søknadId,
                    ).asUpdate
                )
            }
        }

    // Brukt for å matche Oebs-data mot eit Infotrygd-resultat
    override fun hentSøknadIdFraResultat(
        fnrBruker: String,
        saksblokkOgSaksnummer: String,
        vedtaksdato: LocalDate
    ): UUID {
        val uuids: List<UUID> = time("hent_søknadid_fra_resultat") {
            using(sessionOf(ds)) { session ->
                session.run(
                    queryOf(
                        "SELECT SOKNADS_ID FROM V1_INFOTRYGD_DATA WHERE FNR_BRUKER = ? AND SAKSBLOKK = ? AND SAKSNR = ? AND VEDTAKSDATO = ?",
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
        } else if (uuids.count() == 0) {
            throw RuntimeException("Ingen søknadar med korrekt fnr, saksblokk og vedtaksdato!")
        }
        return uuids[0]
    }

    override fun hentVedtaksresultatForSøknad(søknadId: UUID): VedtaksresultatData? {

        return time("hent_søknadid_fra_resultat") {
            using(sessionOf(ds)) { session ->
                session.run(
                    queryOf(
                        "SELECT SOKNADS_ID, FNR_BRUKER, TRYGDEKONTORNR, SAKSBLOKK, SAKSNR, RESULTAT, VEDTAKSDATO FROM V1_INFOTRYGD_DATA WHERE SOKNADS_ID = ?",
                        søknadId,
                    ).map {
                        VedtaksresultatData(
                            søknadId = UUID.fromString(it.string("SOKNADS_ID")),
                            fnrBruker = it.string("FNR_BRUKER"),
                            trygdekontorNr = it.string("TRYGDEKONTORNR"),
                            saksblokk = it.string("SAKSBLOKK"),
                            saksnr = it.string("SAKSNR"),
                            resultat = it.stringOrNull("RESULTAT"),
                            vedtaksdato = it.localDateOrNull("VEDTAKSDATO"),
                        )
                    }.asSingle
                )
            }
        }
    }

    fun getSaksblokk(saksblokkOgSaksnummer: String): Char {
        return saksblokkOgSaksnummer.first()
    }

    fun getSaksnr(saksblokkOgSaksnummer: String): String {
        return saksblokkOgSaksnummer.takeLast(2)
    }

    private inline fun <T : Any?> time(queryName: String, function: () -> T) =
        Prometheus.dbTimer.labels(queryName).startTimer().let { timer ->
            function().also {
                timer.observeDuration()
            }
        }
}
