package no.nav.dagpenger.soknad.mottak.oppslag

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.equalToJson
import com.github.tomakehurst.wiremock.client.WireMock.okJson
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.github.tomakehurst.wiremock.matching.ContentPattern
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.throwable.shouldHaveMessage
import io.mockk.coEvery
import io.mockk.mockkClass
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import wiremock.com.google.common.net.HttpHeaders

internal class PDLClientTest {

    @Test
    fun `Get token then call PDl`() = runBlocking {
        wireMockServer.addPdlResponse(
            equalToJson(PDLClient.aktorQuery("fnr")),
            okJson("""{"data":{"hentIdenter":{"identer": [{"ident": "aktorid","historisk": false,"type": "AKTORID"}]}}}""")
        )

        PDLClient(wireMockServer.baseUrl(), stsMock)
            .getAktorId("fnr") shouldBe "aktorid"
    }

    @Test
    fun `Exception if error from PDL`() = runBlocking {
        wireMockServer.addPdlResponse(
            equalToJson(PDLClient.aktorQuery("fnr")),
            okJson("""{"errors": ["error"]} """)
        )

        shouldThrow<PdlException> {
            PDLClient(wireMockServer.baseUrl(), stsMock).getAktorId("fnr")
        }.shouldHaveMessage("""["error"]""")
    }

    @BeforeEach
    fun reset() {
        StsClientTest.wireMockServer.resetAll()
    }

    companion object {
        private const val TOKEN = "token"
        val wireMockServer by lazy {
            WireMockServer(WireMockConfiguration.options().dynamicPort())
        }

        val stsMock = mockkClass(StsClient::class).also {
            coEvery { it.getToken() } returns TOKEN
        }

        @BeforeAll
        @JvmStatic
        fun setup() {
            wireMockServer.start()
        }

        @AfterAll
        @JvmStatic
        fun teardown() {
            wireMockServer.stop()
        }

        fun WireMockServer.addPdlResponse(requestBody: ContentPattern<*>, response: ResponseDefinitionBuilder) {
            this.addStubMapping(
                post(urlEqualTo("/${PDLClient.PATH}"))
                    .withHeader(HttpHeaders.CONTENT_TYPE, equalTo("application/json"))
                    .withHeader(HttpHeaders.ACCEPT, equalTo("application/json"))
                    .withHeader(HttpHeaders.AUTHORIZATION, equalTo("Bearer $TOKEN"))
                    .withHeader("TEMA", equalTo("DAG"))
                    .withHeader("Nav-Consumer-Token", equalTo("Bearer $TOKEN"))
                    .withRequestBody(requestBody)
                    .willReturn(response)
                    .build()
            )
        }
    }
}
