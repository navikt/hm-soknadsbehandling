package no.nav.dagpenger.soknad.mottak.service

import io.kotest.matchers.shouldBe
import io.ktor.http.HttpMethod.Companion.Get
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.withTestApplication
import io.mockk.every
import io.mockk.mockk
import no.nav.dagpenger.soknad.mottak.api
import no.nav.dagpenger.soknad.mottak.db.SoknadStore
import org.junit.jupiter.api.Test

internal class SøknadMetadataFinderTest {
    @Test
    fun `successful find fagsak id`() {
        val store = mockk<SoknadStore>().also {
            every { it.findFagsakId("soknadsId") } returns "fagsakId"
        }

        withTestApplication(moduleFunction = { api(store) }) {
            with(this.handleRequest(Get, "/api/fagsakid/soknadsId")) {
                response.status() shouldBe HttpStatusCode.OK
                response.content shouldBe "fagsakId"
            }
        }
    }

    @Test
    fun `successful find journalpost id `() {
        val store = mockk<SoknadStore>().also {
            every { it.findJournalpostId("soknadsId") } returns "journalpostId"
        }

        withTestApplication(moduleFunction = { api(store) }) {
            with(this.handleRequest(Get, "/api/journalpost/soknadsId")) {
                response.status() shouldBe HttpStatusCode.OK
                response.content shouldBe "journalpostId"
            }
        }
    }

    @Test
    fun `fagsakid not found`() {
        val store = mockk<SoknadStore>().also {
            every { it.findFagsakId("soknadsId") } returns null
        }

        withTestApplication(moduleFunction = { api(store) }) {
            with(this.handleRequest(Get, "/api/fagsakid/soknadsId")) {
                response.status() shouldBe HttpStatusCode.NotFound
            }
        }
    }

    @Test
    fun ` journalpost id not found `() {
        val store = mockk<SoknadStore>().also {
            every { it.findJournalpostId("soknadsId") } returns null
        }

        withTestApplication(moduleFunction = { api(store) }) {
            with(this.handleRequest(Get, "/api/journalpost/soknadsId")) {
                response.status() shouldBe HttpStatusCode.NotFound
            }
        }
    }

    @Test
    fun `error from backend on fagsakId`() {
        val store = mockk<SoknadStore>().also {
            every { it.findFagsakId("soknadsId") } throws RuntimeException("test")
        }

        withTestApplication(moduleFunction = { api(store) }) {
            with(this.handleRequest(Get, "/api/fagsakid/soknadsId")) {
                response.status() shouldBe HttpStatusCode.InternalServerError
            }
        }
    }

    @Test
    fun `error from backend on journalpostId`() {
        val store = mockk<SoknadStore>().also {
            every { it.findJournalpostId("soknadsId") } throws RuntimeException("test")
        }

        withTestApplication(moduleFunction = { api(store) }) {
            with(this.handleRequest(Get, "/api/journalpost/soknadsId")) {
                response.status() shouldBe HttpStatusCode.InternalServerError
            }
        }
    }
}
