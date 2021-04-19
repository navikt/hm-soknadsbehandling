package no.nav.hjelpemidler.soknad.mottak.service

import org.testcontainers.containers.PostgreSQLContainer

internal object PostgresContainer {
    val instance by lazy {
        PostgreSQLContainer<Nothing>("postgres:13.1").apply {
            start()
        }
    }
}

// TODO Fix test sånn at den funker med mock av http klient
internal class SøknadsgodkjenningServiceTest {

/*
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
*/
}
