package no.nav.hjelpemidler.soknad.mottak.db

import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.matchers.shouldBe
import kotliquery.queryOf
import kotliquery.sessionOf
import no.nav.hjelpemidler.soknad.mottak.mockSøknad
import no.nav.hjelpemidler.soknad.mottak.service.SoknadData
import no.nav.hjelpemidler.soknad.mottak.service.Status
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class SøknadStoreFormidlerTest {

    @Test
    fun `Hent formidlers søknad`() {
        val soknadsId = UUID.randomUUID()
        withMigratedDb {
            SøknadStorePostgres(DataSource.instance).apply {
                this.save(
                    mockSøknad(soknadsId)
                )
            }
            SøknadStoreFormidlerPostgres(DataSource.instance).apply {
                val formidlersSøknad = this.hentSøknaderForFormidler("12345678910", 4)[0]
                assertEquals("fornavn etternavn", formidlersSøknad.navnBruker)
            }
        }
    }

    @Test
    fun `Henter ikke søknad som er 4 uker gammel`() {

        val id = UUID.randomUUID()

        withMigratedDb {
            SøknadStorePostgres(DataSource.instance).apply {
                this.save(
                    SoknadData(
                        "id",
                        "12345678910",
                        id,
                        ObjectMapper().readTree(""" {"key": "value"} """),
                        status = Status.SLETTET,
                        kommunenavn = null

                    )
                ).also {
                    it shouldBe 1
                }
            }
            DataSource.instance.apply {
                sessionOf(this).run(queryOf("UPDATE V1_SOKNAD SET CREATED = (now() - interval '3 week') WHERE SOKNADS_ID = '$id' ").asExecute)
            }

            SøknadStoreFormidlerPostgres(DataSource.instance).apply {
                this.hentSøknaderForFormidler("12345678910", 4).also {
                    it.size shouldBe 1
                }
            }

            DataSource.instance.apply {
                sessionOf(this).run(queryOf("UPDATE V1_SOKNAD SET CREATED = (now() - interval '5 week') WHERE SOKNADS_ID = '$id' ").asExecute)
            }

            SøknadStoreFormidlerPostgres(DataSource.instance).apply {
                this.hentSøknaderForFormidler("1234567891014", 4).also {
                    it.size shouldBe 0
                }
            }
        }
    }

    @Test
    fun `Fullmakt for søknad for formidler`() {

        val soknadId = UUID.randomUUID()

        withMigratedDb {
            SøknadStorePostgres(DataSource.instance).apply {
                this.save(
                    mockSøknad(soknadId, Status.GODKJENT_MED_FULLMAKT)
                ).also {
                    it shouldBe 1
                }

                this.oppdaterStatus(soknadId, Status.ENDELIG_JOURNALFØRT).also {
                    it shouldBe 1
                }
            }
            SøknadStoreFormidlerPostgres(DataSource.instance).apply {
                val søknader = this.hentSøknaderForFormidler("12345678910", 4)
                assertTrue { søknader[0].fullmakt }
            }
        }
    }

    @Test
    fun `Fullmakt er false hvis bruker skal bekrefte søknaden`() {

        val soknadId = UUID.randomUUID()

        withMigratedDb {
            SøknadStorePostgres(DataSource.instance).apply {
                this.save(
                    mockSøknad(soknadId, Status.VENTER_GODKJENNING)
                ).also {
                    it shouldBe 1
                }

                this.oppdaterStatus(
                    soknadId,
                    Status.GODKJENT
                ).also {
                    it shouldBe 1
                }

                this.oppdaterStatus(soknadId, Status.ENDELIG_JOURNALFØRT).also {
                    it shouldBe 1
                }
            }
            SøknadStoreFormidlerPostgres(DataSource.instance).apply {
                val søknader = this.hentSøknaderForFormidler("12345678910", 4)
                assertFalse { søknader[0].fullmakt }
            }
        }
    }
}
