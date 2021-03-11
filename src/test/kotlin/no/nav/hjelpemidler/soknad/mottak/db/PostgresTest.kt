package no.nav.hjelpemidler.soknad.mottak.db

import com.fasterxml.jackson.databind.ObjectMapper
import com.zaxxer.hikari.HikariDataSource
import io.kotest.matchers.shouldBe
import kotliquery.queryOf
import kotliquery.sessionOf
import no.nav.hjelpemidler.soknad.mottak.Configuration
import no.nav.hjelpemidler.soknad.mottak.mockSøknad
import no.nav.hjelpemidler.soknad.mottak.service.Bruksarena
import no.nav.hjelpemidler.soknad.mottak.service.Funksjonsnedsettelse
import no.nav.hjelpemidler.soknad.mottak.service.SoknadData
import no.nav.hjelpemidler.soknad.mottak.service.Status
import org.junit.jupiter.api.Test
import org.testcontainers.containers.PostgreSQLContainer
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull

internal object PostgresContainer {
    val instance by lazy {
        PostgreSQLContainer<Nothing>("postgres:13.1").apply {
            start()
        }
    }
}

internal object DataSource {
    val instance: HikariDataSource by lazy {
        HikariDataSource().apply {
            username = PostgresContainer.instance.username
            password = PostgresContainer.instance.password
            jdbcUrl = PostgresContainer.instance.jdbcUrl
            connectionTimeout = 1000L
        }.also { sessionOf(it).run(queryOf("DROP ROLE IF EXISTS cloudsqliamuser").asExecute) }
            .also { sessionOf(it).run(queryOf("CREATE ROLE cloudsqliamuser").asExecute) }
    }
}

internal fun withCleanDb(test: () -> Unit) = DataSource.instance.also { clean(it) }
    .run { test() }

internal fun withMigratedDb(test: () -> Unit) =
    DataSource.instance.also { clean(it) }
        .also { migrate(it) }.run { test() }

internal class SøknadStoreTest {

    @Test
    fun `Hent lagret soknad`() {
        val soknadsId = UUID.randomUUID()
        withMigratedDb {
            SøknadStorePostgres(DataSource.instance).apply {
                this.save(
                    mockSøknad(soknadsId)
                )
                val hentSoknad = this.hentSoknad(soknadsId)
                assertEquals("15084300133", hentSoknad?.bruker?.fnummer)
                assertEquals("fornavn", hentSoknad?.bruker?.fornavn)
                assertEquals("etternavn", hentSoknad?.bruker?.etternavn)
                assertEquals("12345678", hentSoknad?.bruker?.telefonNummer)
                assertNull(hentSoknad?.bruker?.adresse)
                assertNull(hentSoknad?.bruker?.postnummer)
                assertEquals("Stedet", hentSoknad?.bruker?.poststed)
                assertEquals("formidlerFornavn formidlerEtternavn", hentSoknad?.formidler?.navn)
                assertEquals("arbeidssted", hentSoknad?.formidler?.arbeidssted)
                assertEquals("stilling", hentSoknad?.formidler?.stilling)
                assertEquals("postadresse arbeidssted 1234 poststed", hentSoknad?.formidler?.adresse)
                assertEquals("12345678", hentSoknad?.formidler?.telefon)
                assertEquals("treffedager", hentSoknad?.formidler?.treffesEnklest)
                assertEquals("epost@adad.com", hentSoknad?.formidler?.epost)
                assertNull(hentSoknad?.oppfolgingsansvarlig)
                assertEquals("Hjemme", hentSoknad?.bruker?.boform)
                assertEquals(Bruksarena.DAGLIGLIVET, hentSoknad?.bruker?.bruksarena)
                assertEquals(
                    listOf(Funksjonsnedsettelse.BEVEGELSE, Funksjonsnedsettelse.HØRSEL),
                    hentSoknad?.bruker?.funksjonsnedsettelser
                )

                assertEquals(2, hentSoknad?.hjelpemiddelTotalAntall)
                assertEquals(1, hentSoknad?.hjelpemidler?.size)
                assertEquals(1, hentSoknad?.hjelpemidler?.first()?.antall)
                assertEquals("Hjelpemiddelnavn", hentSoknad?.hjelpemidler?.first()?.navn)
                assertEquals("beskrivelse", hentSoknad?.hjelpemidler?.first()?.beskrivelse)
                assertEquals("Arbeidsstoler", hentSoknad?.hjelpemidler?.first()?.hjelpemiddelkategori)
                assertEquals("123456", hentSoknad?.hjelpemidler?.first()?.hmsNr)
                assertEquals("Tilleggsinformasjon", hentSoknad?.hjelpemidler?.first()?.tilleggsinformasjon)
                assertEquals("1", hentSoknad?.hjelpemidler?.first()?.rangering)
                assertEquals(true, hentSoknad?.hjelpemidler?.first()?.utlevertFraHjelpemiddelsentralen)
                assertEquals(1, hentSoknad?.hjelpemidler?.first()?.vilkarliste?.size)
                assertEquals("Vilkår 1", hentSoknad?.hjelpemidler?.first()?.vilkarliste?.first()?.vilkaarTekst)
                assertEquals("Tilleggsinfo", hentSoknad?.hjelpemidler?.first()?.vilkarliste?.first()?.tilleggsInfo)
                assertEquals(1, hentSoknad?.hjelpemidler?.first()?.tilbehorListe?.size)
                assertEquals("654321", hentSoknad?.hjelpemidler?.first()?.tilbehorListe?.first()?.hmsnr)
                assertEquals("Tilbehør 1", hentSoknad?.hjelpemidler?.first()?.tilbehorListe?.first()?.navn)
                assertEquals(1, hentSoknad?.hjelpemidler?.first()?.tilbehorListe?.first()?.antall)
                assertEquals("begrunnelse", hentSoknad?.hjelpemidler?.first()?.begrunnelse)
                assertEquals(true, hentSoknad?.hjelpemidler?.first()?.kanIkkeTilsvarande)

                assertNull(hentSoknad?.levering?.adresse)
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
                assertEquals("15084300133", hentSoknad?.bruker?.fnummer)
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
}

internal class PostgresTest {

    @Test
    fun `Migration scripts are applied successfully`() {
        withCleanDb {
            val migrations = migrate(DataSource.instance)
            migrations shouldBe 5
        }
    }

    @Test
    fun `JDBC url is set correctly from  config values `() {
        with(hikariConfigFrom(Configuration)) {
            jdbcUrl shouldBe "jdbc:postgresql://host.docker.internal:5434/soknadsbehandling"
        }
    }
}
