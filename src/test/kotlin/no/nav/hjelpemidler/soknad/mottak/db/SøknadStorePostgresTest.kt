package no.nav.hjelpemidler.soknad.mottak.db

import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.matchers.shouldBe
import kotliquery.queryOf
import kotliquery.sessionOf
import no.nav.hjelpemidler.soknad.mottak.mockSøknad
import no.nav.hjelpemidler.soknad.mottak.service.Bruksarena
import no.nav.hjelpemidler.soknad.mottak.service.Funksjonsnedsettelse
import no.nav.hjelpemidler.soknad.mottak.service.SoknadData
import no.nav.hjelpemidler.soknad.mottak.service.Status
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull

internal class SøknadStorePostgresTest {

    @Test
    fun `Hent lagret soknad`() {
        val soknadsId = UUID.randomUUID()
        withMigratedDb {
            SøknadStorePostgres(DataSource.instance).apply {
                this.save(
                    mockSøknad(soknadsId)
                )
                val hentSoknad = this.hentSoknad(soknadsId)
                assertEquals("15084300133", hentSoknad?.søknadsdata?.bruker?.fnummer)
                assertEquals("fornavn", hentSoknad?.søknadsdata?.bruker?.fornavn)
                assertEquals("etternavn", hentSoknad?.søknadsdata?.bruker?.etternavn)
                assertEquals("12345678", hentSoknad?.søknadsdata?.bruker?.telefonNummer)
                assertNull(hentSoknad?.søknadsdata?.bruker?.adresse)
                assertNull(hentSoknad?.søknadsdata?.bruker?.postnummer)
                assertEquals("Stedet", hentSoknad?.søknadsdata?.bruker?.poststed)
                assertEquals("formidlerFornavn formidlerEtternavn", hentSoknad?.søknadsdata?.formidler?.navn)
                assertEquals("arbeidssted", hentSoknad?.søknadsdata?.formidler?.arbeidssted)
                assertEquals("stilling", hentSoknad?.søknadsdata?.formidler?.stilling)
                assertEquals("postadresse arbeidssted 1234 poststed", hentSoknad?.søknadsdata?.formidler?.adresse)
                assertEquals("12345678", hentSoknad?.søknadsdata?.formidler?.telefon)
                assertEquals("treffedager", hentSoknad?.søknadsdata?.formidler?.treffesEnklest)
                assertEquals("epost@adad.com", hentSoknad?.søknadsdata?.formidler?.epost)
                assertNull(hentSoknad?.søknadsdata?.oppfolgingsansvarlig)
                assertEquals("Hjemme", hentSoknad?.søknadsdata?.bruker?.boform)
                assertEquals(Bruksarena.DAGLIGLIVET, hentSoknad?.søknadsdata?.bruker?.bruksarena)
                assertEquals(
                    listOf(Funksjonsnedsettelse.BEVEGELSE, Funksjonsnedsettelse.HØRSEL),
                    hentSoknad?.søknadsdata?.bruker?.funksjonsnedsettelser
                )

                assertEquals(2, hentSoknad?.søknadsdata?.hjelpemiddelTotalAntall)
                assertEquals(1, hentSoknad?.søknadsdata?.hjelpemidler?.size)
                assertEquals(1, hentSoknad?.søknadsdata?.hjelpemidler?.first()?.antall)
                assertEquals("Hjelpemiddelnavn", hentSoknad?.søknadsdata?.hjelpemidler?.first()?.navn)
                assertEquals("beskrivelse", hentSoknad?.søknadsdata?.hjelpemidler?.first()?.beskrivelse)
                assertEquals("Arbeidsstoler", hentSoknad?.søknadsdata?.hjelpemidler?.first()?.hjelpemiddelkategori)
                assertEquals("123456", hentSoknad?.søknadsdata?.hjelpemidler?.first()?.hmsNr)
                assertEquals("Tilleggsinformasjon", hentSoknad?.søknadsdata?.hjelpemidler?.first()?.tilleggsinformasjon)
                assertEquals("1", hentSoknad?.søknadsdata?.hjelpemidler?.first()?.rangering)
                assertEquals(true, hentSoknad?.søknadsdata?.hjelpemidler?.first()?.utlevertFraHjelpemiddelsentralen)
                assertEquals(1, hentSoknad?.søknadsdata?.hjelpemidler?.first()?.vilkarliste?.size)
                assertEquals("Vilkår 1", hentSoknad?.søknadsdata?.hjelpemidler?.first()?.vilkarliste?.first()?.vilkaarTekst)
                assertEquals("Tilleggsinfo", hentSoknad?.søknadsdata?.hjelpemidler?.first()?.vilkarliste?.first()?.tilleggsInfo)
                assertEquals(1, hentSoknad?.søknadsdata?.hjelpemidler?.first()?.tilbehorListe?.size)
                assertEquals("654321", hentSoknad?.søknadsdata?.hjelpemidler?.first()?.tilbehorListe?.first()?.hmsnr)
                assertEquals("Tilbehør 1", hentSoknad?.søknadsdata?.hjelpemidler?.first()?.tilbehorListe?.first()?.navn)
                assertEquals(1, hentSoknad?.søknadsdata?.hjelpemidler?.first()?.tilbehorListe?.first()?.antall)
                assertEquals("begrunnelse", hentSoknad?.søknadsdata?.hjelpemidler?.first()?.begrunnelse)
                assertEquals(true, hentSoknad?.søknadsdata?.hjelpemidler?.first()?.kanIkkeTilsvarande)

                assertNull(hentSoknad?.søknadsdata?.levering?.adresse)
            }
        }
    }

    @Test
    fun `Hent lagret soknad 2`() {
        val soknadsId = UUID.randomUUID()
        withMigratedDb {
            SøknadStorePostgres(DataSource.instance).apply {
                this.save(
                    SoknadData(
                        "15084300133",
                        "id2",
                        soknadsId,
                        ObjectMapper().readTree(
                            """ {
                          "fnrBruker": "15084300133",
                          "soknadId": "62f68547-11ae-418c-8ab7-4d2af985bcd9",
                          "datoOpprettet": "2021-02-23T09:46:45.146+00:00",
                          "soknad": {
                            "id": "e8dac11d-fa66-4561-89d7-88a62ab31c2b",
                            "date": "2021-02-16",
                            "bruker": {
                              "kilde": "PDL",
                              "adresse": "Trandemveien 29",
                              "fnummer": "15084300133",
                              "fornavn": "Sedat",
                              "poststed": "Hebnes",
                              "signatur": "BRUKER_BEKREFTER",
                              "etternavn": "Kronjuvel",
                              "postnummer": "4235",
                              "telefonNummer": "12341234"
                            },
                            "levering": {
                              "hmfEpost": "anders@andersen.no",
                              "hmfPostnr": "1212",
                              "hmfFornavn": "Sedat",
                              "hmfTelefon": "12121212",
                              "opfFornavn": "",
                              "opfTelefon": "",
                              "hmfPoststed": "Oslo",
                              "hmfStilling": "Ergo",
                              "opfStilling": "",
                              "hmfEtternavn": "Kronjuvel",
                              "opfAnsvarFor": "",
                              "opfEtternavn": "",
                              "hmfArbeidssted": "Oslo",
                              "hmfPostadresse": "Oslovegen",
                              "opfArbeidssted": "",
                              "opfRadioButton": "Hjelpemiddelformidler",
                              "utleveringPostnr": "",
                              "hmfTreffesEnklest": "Måndag",
                              "utleveringFornavn": "",
                              "utleveringTelefon": "",
                              "utleveringPoststed": "",
                              "utleveringEtternavn": "",
                              "merknadTilUtlevering": "",
                              "utleveringPostadresse": "",
                              "utleveringsmaateRadioButton": "AlleredeUtlevertAvNav"
                            },
                            "hjelpemidler": {
                              "hjelpemiddelListe": [
                                {
                                  "navn": "Topro Skråbrett",
                                  "hmsNr": "243544",
                                  "antall": 1,
                                  "produkt": {
                                    "artid": "108385",
                                    "artno": "815061",
                                    "newsid": "4289",
                                    "prodid": "30389",
                                    "apostid": "860",
                                    "apostnr": "3",
                                    "artname": "Topro Skråbrett",
                                    "isocode": "18301505",
                                    "stockid": "243544",
                                    "isotitle": "Terskeleliminatorer",
                                    "kategori": "Terskeleliminatorer og ramper",
                                    "postrank": "1",
                                    "prodname": "Topro Skråbrett",
                                    "artpostid": "14309",
                                    "adescshort": "Bredde 90 cm. Lengde 77 cm.",
                                    "aposttitle": "Post 3: Terskeleleminator - påkjøring fra en side. Velegnet for utendørs bruk",
                                    "pshortdesc": "Skråbrett i aluminium utførelse med sklisikker overflate. Leveres som standard i bredder fra 90 - 126 cm og justerbar høyde fra 5 - 20 cm.",
                                    "cleanposttitle": "Terskeleleminator - påkjøring fra en side. Velegnet for utendørs bruk",
                                    "techdataAsText": "Påkjøring forfra JA, Bredde 90cm, Lengde maks 77cm, Terskelhøyde min 5cm, Terskelhøyde maks 20cm, Vekt 8kg, Belastning maks 350kg, Fastmontert JA, Festemåte fastmontert, Materiale aluminium, Sklisikker overflate JA",
                                    "cleanTechdataAsText": " Bredde 90cm,  Lengde maks 77cm,  Terskelhøyde min 5cm,  Terskelhøyde maks 20cm"
                                  },
                                  "uniqueKey": "2435441613472031819",
                                  "beskrivelse": "Topro Skråbrett",
                                  "begrunnelsen": "",
                                  "tilbehorListe": [],
                                  "vilkaroverskrift": "",
                                  "kanIkkeTilsvarande": "false",
                                  "tilleggsinformasjon": "",
                                  "hjelpemiddelkategori": "Terskeleliminatorer og ramper",
                                  "utlevertFraHjelpemiddelsentralen": false
                                }
                              ],
                              "hjelpemiddelTotaltAntall": 1
                            },
                            "brukersituasjon": {
                              "storreBehov": true,
                              "nedsattFunksjon": true,
                              "praktiskeProblem": true,
                              "bostedRadioButton": "Hjemme",
                              "nedsattFunksjonTypes": {
                                "horsel": false,
                                "bevegelse": true,
                                "kognisjon": false
                              },
                              "bruksarenaErDagliglivet": true
                            }
                          }
                        } """
                        ),
                        status = Status.VENTER_GODKJENNING,
                        kommunenavn = null
                    )
                )
                val hentSoknad = this.hentSoknad(soknadsId)
                assertEquals("15084300133", hentSoknad?.søknadsdata?.bruker?.fnummer)
            }
        }
    }

    @Test
    fun `Store soknad`() {
        withMigratedDb {
            SøknadStorePostgres(DataSource.instance).apply {
                this.save(
                    SoknadData(
                        "id",
                        "id2",
                        UUID.randomUUID(),
                        ObjectMapper().readTree(""" {"key": "value"} """),
                        status = Status.VENTER_GODKJENNING,
                        kommunenavn = null

                    )
                ).also {
                    it shouldBe 1
                }
            }
        }
    }

    @Test
    fun `Søknad is utgått`() {

        val id = UUID.randomUUID()

        withMigratedDb {
            SøknadStorePostgres(DataSource.instance).apply {
                this.save(
                    SoknadData(
                        "id",
                        "id2",
                        id,
                        ObjectMapper().readTree(""" {"key": "value"} """),
                        status = Status.VENTER_GODKJENNING,
                        kommunenavn = null

                    )
                ).also {
                    it shouldBe 1
                }
            }
            DataSource.instance.apply {
                sessionOf(this).run(queryOf("UPDATE V1_SOKNAD SET CREATED = (now() - interval '2 week') WHERE SOKNADS_ID = '$id' ").asExecute)
            }

            SøknadStorePostgres(DataSource.instance).apply {
                this.hentSoknaderTilGodkjenningEldreEnn(14).also {
                    it.size shouldBe 1
                }
            }

            DataSource.instance.apply {
                sessionOf(this).run(queryOf("UPDATE V1_SOKNAD SET CREATED = (now() - interval '13 day') WHERE SOKNADS_ID = '$id' ").asExecute)
            }

            SøknadStorePostgres(DataSource.instance).apply {
                this.hentSoknaderTilGodkjenningEldreEnn(14).also {
                    it.size shouldBe 0
                }
            }
        }
    }

    @Test
    fun `Oppdater journalpostId og ikke overskriv eksisterende journalpostId`() {
        val id = UUID.randomUUID()
        val journalpostId = "453645864"
        withMigratedDb {
            SøknadStorePostgres(DataSource.instance).apply {
                this.save(
                    SoknadData(
                        "id",
                        "id2",
                        id,
                        ObjectMapper().readTree(""" {"key": "value"} """),
                        status = Status.GODKJENT_MED_FULLMAKT,
                        kommunenavn = null
                    )
                )
            }

            SøknadStorePostgres(DataSource.instance).apply {
                this.oppdaterJournalpostId(id, journalpostId).also {
                    it shouldBe 1
                }
            }

            SøknadStorePostgres(DataSource.instance).apply {
                this.oppdaterJournalpostId(id, journalpostId).also {
                    it shouldBe 0
                }
            }
        }
    }

    @Test
    fun `Oppdater oppgaveId og ikke overskriv eksisterende oppgaveId`() {
        val id = UUID.randomUUID()
        val oppgaveId = "57983"
        withMigratedDb {
            SøknadStorePostgres(DataSource.instance).apply {
                this.save(
                    SoknadData(
                        "id",
                        "id2",
                        id,
                        ObjectMapper().readTree(""" {"key": "value"} """),
                        status = Status.GODKJENT_MED_FULLMAKT,
                        kommunenavn = null
                    )
                )
            }

            SøknadStorePostgres(DataSource.instance).apply {
                this.oppdaterOppgaveId(id, oppgaveId).also {
                    it shouldBe 1
                }
            }

            SøknadStorePostgres(DataSource.instance).apply {
                this.oppdaterOppgaveId(id, oppgaveId).also {
                    it shouldBe 0
                }
            }
        }
    }
}
