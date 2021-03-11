package no.nav.hjelpemidler.soknad.mottak.service

import com.zaxxer.hikari.HikariDataSource
import io.kotest.matchers.shouldBe
import kotliquery.queryOf
import kotliquery.sessionOf
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.hjelpemidler.soknad.mottak.db.SøknadStorePostgres
import no.nav.hjelpemidler.soknad.mottak.db.clean
import no.nav.hjelpemidler.soknad.mottak.db.migrate
import no.nav.hjelpemidler.soknad.mottak.mockSøknad
import org.junit.jupiter.api.Test
import org.testcontainers.containers.PostgreSQLContainer
import java.util.UUID
import kotlin.test.assertEquals

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

internal fun withMigratedDb(test: () -> Unit) =
    DataSource.instance.also { clean(it) }
        .also { migrate(it) }.run { test() }

internal class SøknadsgodkjenningServiceTest {

    @Test
    fun `Søknad is markert som utløpt`() {

        val id = UUID.randomUUID()
        var søknadsgodkjenningService: SøknadsgodkjenningService

        withMigratedDb {
            SøknadStorePostgres(DataSource.instance).apply {

                this.save(
                    mockSøknad(id)
                ).also {
                    it shouldBe 1
                }
                val storePostgres = this
                TestRapid().apply {
                    søknadsgodkjenningService = SøknadsgodkjenningService(storePostgres, this)
                }
            }
            DataSource.instance.apply {
                sessionOf(this).run(queryOf("UPDATE V1_SOKNAD SET CREATED = (now() - interval '15 day') WHERE SOKNADS_ID = '$id' ").asExecute)
            }

            val utgåtteSøknader = søknadsgodkjenningService.slettUtgåtteSøknader()
            assertEquals(1, utgåtteSøknader)

            SøknadStorePostgres(DataSource.instance).apply {
                val søknad = this.hentSoknad(id)
                assertEquals(Status.UTLØPT, søknad!!.status)
            }
        }
    }
}
