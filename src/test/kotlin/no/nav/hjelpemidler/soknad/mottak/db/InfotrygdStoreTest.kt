package no.nav.hjelpemidler.soknad.mottak.db

import io.kotest.matchers.shouldBe
import no.nav.hjelpemidler.soknad.mottak.service.VedtaksresultatData
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull

internal class InfotrygdStoreTest {

    @Test
    fun `Lag knytning mellom endeleg journalført digital søknad og Infotrygd basert på fagsakId`() {

        val søknadId = UUID.randomUUID() // Digital søknad får denne i kanalreferanseId frå Joark
        val fnrBruker = "15084300133"
        val fagsakId = "4703C13"

        val vedtaksresultatData = VedtaksresultatData(
            søknadId,
            fnrBruker,
            VedtaksresultatData.getTrygdekontorNrFromFagsakId(fagsakId),
            VedtaksresultatData.getSaksblokkFromFagsakId(fagsakId),
            VedtaksresultatData.getSaksnrFromFagsakId(fagsakId),
            null,
            null
        )

        withMigratedDb {
            InfotrygdStorePostgres(DataSource.instance).apply {
                this.lagKnytningMellomFagsakOgSøknad(vedtaksresultatData)
                val søknad: VedtaksresultatData? = this.hentVedtaksresultatForSøknad(søknadId)
                assertEquals("15084300133", søknad?.fnrBruker)
                assertEquals("4703", søknad?.trygdekontorNr)
                assertEquals("C", søknad?.saksblokk)
                assertEquals("13", søknad?.saksnr)
                assertNull(søknad?.vedtaksresultat)
                assertNull(søknad?.vedtaksdato)
            }
        }
    }

    @Test
    fun `Lagr vedtaksresultat frå Infotrygd`() {
        val søknadId = UUID.randomUUID()
        val fnrBruker = "15084300133"
        val fagsakId = "4703C13"

        // Før vedtak blir gjort
        val vedtaksresultatData = VedtaksresultatData(
            søknadId,
            fnrBruker,
            VedtaksresultatData.getTrygdekontorNrFromFagsakId(fagsakId),
            VedtaksresultatData.getSaksblokkFromFagsakId(fagsakId),
            VedtaksresultatData.getSaksnrFromFagsakId(fagsakId),
            null,
            null
        )

        // Etter vedtak er gjort
        val resultat = "IM"
        val vedtaksdato = LocalDate.of(2021, 5, 31)

        withMigratedDb {
            InfotrygdStorePostgres(DataSource.instance).apply {
                this.lagKnytningMellomFagsakOgSøknad(vedtaksresultatData)
            }
            InfotrygdStorePostgres(DataSource.instance).apply {
                this.lagreVedtaksresultat(søknadId, fnrBruker, fagsakId, resultat, vedtaksdato)
                    .also {
                        it shouldBe (1)
                    }
            }
            InfotrygdStorePostgres(DataSource.instance).apply {
                val søknad = this.hentVedtaksresultatForSøknad(søknadId)
                assertEquals("15084300133", søknad?.fnrBruker)
                assertEquals("4703", søknad?.trygdekontorNr)
                assertEquals("C", søknad?.saksblokk)
                assertEquals("13", søknad?.saksnr)
                assertEquals("IM", søknad?.vedtaksresultat)
                assertEquals(LocalDate.of(2021, 5, 31).toString(), søknad?.vedtaksdato.toString())
            }
        }
    }

    @Test
    fun `Hent søknadId frå resultat`() {
        val søknadId = UUID.fromString("62f68547-11ae-418c-8ab7-4d2af985bcd9")
        val fnrBruker = "15084300133"
        val fagsakId = "4703C13"

        val vedtaksresultatData = VedtaksresultatData(
            søknadId,
            fnrBruker,
            VedtaksresultatData.getTrygdekontorNrFromFagsakId(fagsakId),
            VedtaksresultatData.getSaksblokkFromFagsakId(fagsakId),
            VedtaksresultatData.getSaksnrFromFagsakId(fagsakId),
            null,
            null
        )

        val resultat = "IM"
        val vedtaksdato = LocalDate.of(2021, 5, 31)

        withMigratedDb {
            InfotrygdStorePostgres(DataSource.instance).apply {
                this.lagKnytningMellomFagsakOgSøknad(vedtaksresultatData)
            }
            InfotrygdStorePostgres(DataSource.instance).apply {
                this.lagreVedtaksresultat(søknadId, fnrBruker, fagsakId, resultat, vedtaksdato)
                    .also {
                        it shouldBe (1)
                    }
            }
            InfotrygdStorePostgres(DataSource.instance).apply {
                val søknadIdResultat = this.hentSøknadIdFraVedtaksresultat(fnrBruker, "C13", LocalDate.of(2021, 5, 31))
                assertEquals("62f68547-11ae-418c-8ab7-4d2af985bcd9", søknadIdResultat.toString())
            }
        }
    }

    // Fleire enn eitt treff gjer det umogleg å matche Oebs-data mot éin søknad
    @Test
    fun `Hent søknadId frå resultat skal returnere null viss det ikkje er nøyaktig eitt treff`() {
        val fnrBruker = "15084300133"
        val søknadId1 = UUID.fromString("62f68547-11ae-418c-8ab7-4d2af985bcd9")
        val fagsakId1 = "4703C13"

        val vedtaksresultatData1 = VedtaksresultatData(
            søknadId1,
            fnrBruker,
            VedtaksresultatData.getTrygdekontorNrFromFagsakId(fagsakId1),
            VedtaksresultatData.getSaksblokkFromFagsakId(fagsakId1),
            VedtaksresultatData.getSaksnrFromFagsakId(fagsakId1),
            null,
            null
        )

        val søknadId2 = UUID.fromString("13a91147-88ae-428c-1ab7-3d2af985bcd9")
        val fagsakId2 = "4719C13"

        val vedtaksresultatData2 = VedtaksresultatData(
            søknadId2,
            fnrBruker,
            VedtaksresultatData.getTrygdekontorNrFromFagsakId(fagsakId2),
            VedtaksresultatData.getSaksblokkFromFagsakId(fagsakId2),
            VedtaksresultatData.getSaksnrFromFagsakId(fagsakId2),
            null,
            null
        )

        val resultat = "IM"
        val vedtaksdato = LocalDate.of(2021, 5, 31)

        withMigratedDb {
            InfotrygdStorePostgres(DataSource.instance).apply {
                this.lagKnytningMellomFagsakOgSøknad(vedtaksresultatData1)
                this.lagKnytningMellomFagsakOgSøknad(vedtaksresultatData2)

                InfotrygdStorePostgres(DataSource.instance).apply {
                    this.lagreVedtaksresultat(søknadId1, fnrBruker, fagsakId1, resultat, vedtaksdato)
                        .also {
                            it shouldBe (1)
                        }
                    this.lagreVedtaksresultat(søknadId2, fnrBruker, fagsakId2, resultat, vedtaksdato)
                        .also {
                            it shouldBe (1)
                        }
                }
                InfotrygdStorePostgres(DataSource.instance).apply {
                    val alteredLines = this.hentSøknadIdFraVedtaksresultat(fnrBruker, "C13", LocalDate.of(2021, 5, 31))
                    assertEquals(null, alteredLines)
                }
            }
        }
    }
}
