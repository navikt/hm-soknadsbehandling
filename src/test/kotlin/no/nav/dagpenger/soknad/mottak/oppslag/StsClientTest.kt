package no.nav.dagpenger.soknad.mottak.oppslag

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.okJson
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import wiremock.com.google.common.net.HttpHeaders

internal class StsClientTest {

    @Test
    fun `Get token`() = runBlocking {
        wireMockServer.addTokenProviderResponse(
            okJson("""{"access_token":"token", "expires_in":3600}""")
        )
        StsClient(wireMockServer.baseUrl(), "user", "pwd").getToken() shouldBe "token"
    }

    @Test
    fun `Only get token once if token is valid`() = runBlocking {
        wireMockServer.addTokenProviderResponse(
            okJson("""{"access_token":"token", "expires_in":3600}""")
        )

        StsClient(wireMockServer.baseUrl(), "user", "pwd").apply {
            (1..3).forEach { this.getToken() }
        }

        wireMockServer.verify(1, getRequestedFor((urlEqualTo("/${StsClient.PATH}"))))
    }

    @Test
    fun `Refresh if token is expired in less than 10 minutes`() = runBlocking {
        wireMockServer.addTokenProviderResponse(
            okJson("""{"access_token":"token", "expires_in":60}""")
        )

        StsClient(wireMockServer.baseUrl(), "user", "pwd").apply {
            (1..3).forEach { this.getToken() }
        }

        wireMockServer.verify(3, getRequestedFor((urlEqualTo("/${StsClient.PATH}"))))
    }

    @BeforeEach
    fun reset() {
        wireMockServer.resetAll()
    }

    companion object {
        val wireMockServer by lazy {
            WireMockServer(WireMockConfiguration.options().dynamicPort())
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

        fun WireMockServer.addTokenProviderResponse(response: ResponseDefinitionBuilder) {
            this.addStubMapping(
                get(urlEqualTo("/${StsClient.PATH}"))
                    .withHeader(HttpHeaders.ACCEPT, equalTo("application/json"))
                    .withBasicAuth("user", "pwd")
                    .willReturn(response)
                    .build()
            )
        }
    }
}
