package no.nav.hjelpemidler.soknad.mottak.db

import com.fasterxml.jackson.databind.ObjectMapper
import com.zaxxer.hikari.HikariDataSource
import io.kotest.matchers.shouldBe
import kotliquery.queryOf
import kotliquery.sessionOf
import no.nav.hjelpemidler.soknad.mottak.Configuration
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

internal class SoknadStoreTest {

    @Test
    fun `Hent lagret soknad`() {
        val soknadsId = UUID.randomUUID()
        withMigratedDb {
            SoknadStorePostgres(DataSource.instance).apply {
                this.save(
                    SoknadData(
                        "15084300133",
                        "id2",
                        "navn",
                        soknadsId,
                        """ {
                          "fnrBruker": "15084300133",
                          "soknadId": "62f68547-11ae-418c-8ab7-4d2af985bcd9",
                          "datoOpprettet": "2021-02-23T09:46:45.146+00:00",
                          "soknad": {
                              "id": "62f68547-11ae-418c-8ab7-4d2af985bcd9",
                              "date": "2020-06-19",
                              "bruker": {
                                "fnummer": "15084300133",
                                "fornavn": "fornavn",
                                "signatur": "FULLMAKT",
                                "etternavn": "etternavn",
                                "telefonNummer": "12345678",
                                "poststed": "Stedet"
                              },
                              "brukersituasjon": {
                                "bostedRadioButton": "Hjemme",
                                "bruksarenaErDagliglivet": true,
                                "nedsattFunksjonTypes": {
                                    "bevegelse": true,
                                    "kognisjon": false,
                                    "horsel": true
                                }
                              }, 
                              "hjelpemidler": {
                            "hjelpemiddelTotaltAntall": 2,
                            "hjelpemiddelListe": [
                              {
                                "uniqueKey": "1234561592555082660",
                                "hmsNr": "123456",
                                "beskrivelse": "beskrivelse",
                                "begrunnelsen": "begrunnelse",
                                "antall": 1,
                                "navn": "Hjelpemiddelnavn",
                                "utlevertFraHjelpemiddelsentralen": true,
                                "tilleggsinformasjon": "Tilleggsinformasjon",
                                "kanIkkeTilsvarande": true,
                                "hjelpemiddelkategori": "Arbeidsstoler",
                                "produkt": {
                                  "postrank": "1"
                                },
                                "vilkarliste": [
                                  {
                                    "id": 1,
                                    "vilkartekst": "Vilkår 1",
                                    "tilleggsinfo": "Tilleggsinfo",
                                    "checked": true
                                  }
                                ],
                                "tilbehorListe": [
                                  {
                                    "hmsnr": "654321",
                                    "navn": "Tilbehør 1",
                                    "antall": 1
                                  }
                                ]
                              }
                            ]
                          },
                              "levering": {
                                 "hmfFornavn": "formidlerFornavn",
                                 "hmfEtternavn": "formidlerEtternavn", 
                                 "hmfArbeidssted": "arbeidssted",
                                  "hmfStilling": "stilling",
                                  "hmfPostadresse": "postadresse arbeidssted",
                                  "hmfPostnr": "1234",
                                  "hmfPoststed": "poststed",
                                  "hmfTelefon": "12345678",
                                  "hmfTreffesEnklest": "treffedager",
                                  "hmfEpost": "epost@adad.com",
                                   "opfRadioButton": "Hjelpemiddelformidler",
                                   "utleveringsmaateRadioButton": "FolkeregistrertAdresse",
                                   "utleveringskontaktpersonRadioButton": "Hjelpemiddelbruker"
                              }
                          }
                        } """,
                        ObjectMapper().readTree(
                            """  {"key": "value"} """
                        ),
                        status = Status.VENTER_GODKJENNING
                    )
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
                assertEquals(listOf(Funksjonsnedsettelse.BEVEGELSE, Funksjonsnedsettelse.HØRSEL), hentSoknad?.bruker?.funksjonsnedsettelser)

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
    fun `Store soknad`() {
        withMigratedDb {
            SoknadStorePostgres(DataSource.instance).apply {
                this.save(
                    SoknadData(
                        "id",
                        "id2",
                        "navn",
                        UUID.randomUUID(),
                        """ {"key": "value"} """,
                        ObjectMapper().readTree(""" {"key": "value"} """),
                        status = Status.VENTER_GODKJENNING
                    )
                ).also {
                    it shouldBe 1
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
            migrations shouldBe 3
        }
    }

    @Test
    fun `JDBC url is set correctly from  config values `() {
        with(hikariConfigFrom(Configuration)) {
            jdbcUrl shouldBe "jdbc:postgresql://host.docker.internal:5434/soknadsbehandling"
        }
    }
}
