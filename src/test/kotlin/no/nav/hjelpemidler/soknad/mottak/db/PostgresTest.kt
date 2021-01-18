package no.nav.hjelpemidler.soknad.mottak.db

import com.zaxxer.hikari.HikariDataSource
import io.kotest.matchers.shouldBe
import no.nav.hjelpemidler.soknad.mottak.Configuration
import no.nav.hjelpemidler.soknad.mottak.service.SoknadData
import org.junit.jupiter.api.Test
import org.testcontainers.containers.PostgreSQLContainer

internal object PostgresContainer {
    val instance by lazy {
        PostgreSQLContainer<Nothing>("postgres:11.2").apply {
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
        }
    }
}

internal fun withCleanDb(test: () -> Unit) = DataSource.instance.also { clean(it) }.run { test() }

internal fun withMigratedDb(test: () -> Unit) =
    DataSource.instance.also { clean(it) }.also { migrate(it) }.run { test() }

internal class SoknadStoreTest {

    @Test
    fun `Store soknad`() {
        withMigratedDb {
            SoknadStorePostgres(DataSource.instance).apply {
                this.save(SoknadData("id", "00000000000", """ {"key": "value"} """)).also {
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
            migrations shouldBe 1
        }
    }

    @Test
    fun `JDBC url is set correctly from  config values `() {
        with(hikariConfigFrom(Configuration)) {
            jdbcUrl shouldBe "jdbc:postgresql://localhost:5434/soknadsbehandling"
        }
    }
}
