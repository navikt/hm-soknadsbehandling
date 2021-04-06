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
    fun lagKnytningMellomFagsakOgSøknad(vedtaksresultatData: VedtaksresultatData): Int
    fun lagreVedtaksresultat(
        søknadId: UUID,
        fnrBruker: String,
        fagsakId: String,
        vedtaksresultat: String,
        vedtaksdato: LocalDate
    ): Int

    fun hentSøknadIdFraVedtaksresultat(fnrBruker: String, saksblokkOgSaksnr: String, vedtaksdato: LocalDate): UUID?
    fun hentVedtaksresultatForSøknad(søknadId: UUID): VedtaksresultatData?
}

internal class InfotrygdStorePostgres(private val ds: DataSource) : InfotrygdStore {

    // EndeligJournalført frå Joark vil opprette linja, og denne blir berika seinare av Infotrygd med resultat og vedtaksdato
    override fun lagKnytningMellomFagsakOgSøknad(vedtaksresultatData: VedtaksresultatData): Int =
        time("insert_knytning_mellom_søknad_og_fagsak") {
            using(sessionOf(ds)) { session ->
                session.run(
                    queryOf(
                        "INSERT INTO V1_INFOTRYGD_DATA (SOKNADS_ID, FNR_BRUKER, TRYGDEKONTORNR, SAKSBLOKK, SAKSNR, VEDTAKSRESULTAT, VEDTAKSDATO ) VALUES (?,?,?,?,?,?,?) ON CONFLICT DO NOTHING",
                        vedtaksresultatData.søknadId,
                        vedtaksresultatData.fnrBruker,
                        vedtaksresultatData.trygdekontorNr,
                        vedtaksresultatData.saksblokk,
                        vedtaksresultatData.saksnr,
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
        vedtaksresultat: String,
        vedtaksdato: LocalDate
    ): Int =
        time("oppdater_vedtaksresultat") {
            using(sessionOf(ds)) { session ->
                session.run(
                    queryOf(
                        "UPDATE V1_INFOTRYGD_DATA SET VEDTAKSRESULTAT = ?, VEDTAKSDATO = ? WHERE SOKNADS_ID = ?",
                        vedtaksresultat,
                        vedtaksdato,
                        søknadId,
                    ).asUpdate
                )
            }
        }

    // Brukt for å matche Oebs-data mot eit Infotrygd-resultat
    override fun hentSøknadIdFraVedtaksresultat(
        fnrBruker: String,
        saksblokkOgSaksnr: String,
        vedtaksdato: LocalDate
    ): UUID? {
        val uuids: List<UUID> = time("hent_søknadid_fra_resultat") {
            using(sessionOf(ds)) { session ->
                session.run(
                    queryOf(
                        "SELECT SOKNADS_ID FROM V1_INFOTRYGD_DATA WHERE FNR_BRUKER = ? AND SAKSBLOKK = ? AND SAKSNR = ? AND VEDTAKSDATO = ?",
                        fnrBruker,
                        saksblokkOgSaksnr.first(),
                        saksblokkOgSaksnr.takeLast(2),
                        vedtaksdato,
                    ).map {
                        UUID.fromString(it.string("SOKNADS_ID"))
                    }.asList
                )
            }
        }
        if (uuids.count() != 1) return null
        return uuids[0]
    }

    override fun hentVedtaksresultatForSøknad(søknadId: UUID): VedtaksresultatData? {
        return time("hent_søknadid_fra_resultat") {
            using(sessionOf(ds)) { session ->
                session.run(
                    queryOf(
                        "SELECT SOKNADS_ID, FNR_BRUKER, TRYGDEKONTORNR, SAKSBLOKK, SAKSNR, VEDTAKSRESULTAT, VEDTAKSDATO FROM V1_INFOTRYGD_DATA WHERE SOKNADS_ID = ?",
                        søknadId,
                    ).map {
                        VedtaksresultatData(
                            søknadId = UUID.fromString(it.string("SOKNADS_ID")),
                            fnrBruker = it.string("FNR_BRUKER"),
                            trygdekontorNr = it.string("TRYGDEKONTORNR"),
                            saksblokk = it.string("SAKSBLOKK"),
                            saksnr = it.string("SAKSNR"),
                            vedtaksresultat = it.stringOrNull("VEDTAKSRESULTAT"),
                            vedtaksdato = it.localDateOrNull("VEDTAKSDATO"),
                        )
                    }.asSingle
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
