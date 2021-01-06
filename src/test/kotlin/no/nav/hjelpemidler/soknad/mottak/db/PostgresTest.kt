package no.nav.hjelpemidler.soknad.mottak.db

import com.zaxxer.hikari.HikariDataSource
import io.kotest.matchers.shouldBe
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.hjelpemidler.soknad.mottak.Configuration
import no.nav.hjelpemidler.soknad.mottak.service.JournalPost
import no.nav.hjelpemidler.soknad.mottak.service.SoknadData
import no.nav.hjelpemidler.soknad.mottak.service.SoknadJournalpostMapping
import org.junit.jupiter.api.Test
import org.testcontainers.containers.PostgreSQLContainer
import java.time.LocalDateTime

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
    fun `Save journal post`() {
        withMigratedDb {
            SoknadStorePostgres(DataSource.instance).apply {
                this.save(
                    JournalPost(
                        "id",
                        "00000000000",
                        "00000000000",
                        "fagsak",
                        "4444",
                        "NY_SØKNAD",
                        LocalDateTime.parse("2020-03-21T12:13:39")
                    )
                ).also {
                    it shouldBe 1
                }
            }
        }
    }

    @Test
    fun `Store journal post with null values`() {
        withMigratedDb {
            SoknadStorePostgres(DataSource.instance).apply {
                this.save(JournalPost("id", "00000000000", "00000000000", null, "4444", "NY_SØKNAD", null)).also {
                    it shouldBe 1
                }
            }
        }
    }

    @Test
    fun `Store journal post does nothing if conflicting keys`() {
        withMigratedDb {
            SoknadStorePostgres(DataSource.instance).apply {
                val journalPost = JournalPost("id", "00000000000", "00000000000", "fagsakid", "4444", "NY_SØKNAD", null)

                this.save(journalPost).also {
                    it shouldBe 1
                }

                this.save(journalPost).also {
                    it shouldBe 0
                }

                using(sessionOf(DataSource.instance)) { session ->
                    session.run(
                        queryOf("SELECT * FROM V1_JOURNAL_POST").map {
                            JournalPost(
                                journalpostId = it.string("JOURNALPOST_ID"),
                                aktørId = it.string("AKTOER_ID"),
                                naturligIdent = it.string("FNR"),
                                fagsakId = it.string("FAGSAK_ID"),
                                behandlendeEnhet = it.string("BEHANDLENDE_ENHET"),
                                henvendelsestype = it.string("HENVENDELSES_TYPE"),
                                datoRegistrert = null
                            )
                        }
                            .asList
                    )
                } shouldBe listOf(journalPost)
            }
        }
    }

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

    @Test
    fun `null if unable to find fagsakId`() {
        withMigratedDb {
            SoknadStorePostgres(DataSource.instance).apply {
                this.findFagsakId("hubba") shouldBe null
            }
        }
    }

    @Test
    fun `Store and find søknad journalpost mapping`() {
        withMigratedDb {
            SoknadStorePostgres(DataSource.instance).apply {
                this.save(
                    JournalPost(
                        journalpostId = "jpid",
                        aktørId = "aktorid",
                        naturligIdent = "ident",
                        fagsakId = "fagsakId",
                        behandlendeEnhet = "enhet",
                        henvendelsestype = "NY_SØKNAD",
                        datoRegistrert = LocalDateTime.now()
                    )
                )

                this.save(
                    SoknadJournalpostMapping(
                        søknadsId = "søknadId",
                        journalpostId = "jpid"
                    )
                ).also {
                    it shouldBe 1
                }

                this.findFagsakId("søknadId").also {
                    it shouldBe "fagsakId"
                }

                this.findJournalpostId("søknadId").also {
                    it shouldBe "jpid"
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
            migrations shouldBe 7
        }
    }

    @Test
    fun `JDBC url is set correctly from  config values `() {
        with(hikariConfigFrom(Configuration)) {
            jdbcUrl shouldBe "jdbc:postgresql://localhost:5432/dp-soknad"
        }
    }
}
