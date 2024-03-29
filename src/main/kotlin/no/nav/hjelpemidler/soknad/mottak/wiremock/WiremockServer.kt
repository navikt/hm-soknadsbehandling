package no.nav.hjelpemidler.soknad.mottak.wiremock

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import no.nav.hjelpemidler.soknad.mottak.Configuration

internal class WiremockServer(private val configuration: Configuration) {

    fun startServer() {
        val wiremockServer = WireMockServer(9098)
        wiremockServer
            .stubFor(
                WireMock.post(WireMock.urlPathMatching("/${configuration.azure.tenantId}/oauth2/v2.0/token"))
                    .willReturn(
                        WireMock.aResponse().withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody(
                                """{
                        "token_type": "Bearer",
                        "expires_in": 3599,
                        "access_token": "1234abc"
                    }"""
                            )
                    )
            )

        wiremockServer
            .stubFor(
                WireMock.post(WireMock.urlPathMatching("/pdl"))
                    .willReturn(
                        WireMock.aResponse().withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody(
                                """
                                {
                                  "data": {
                                    "hentPerson": {
                                      "bostedsadresse": [
                                        {
                                          "vegadresse": {
                                            "kommunenummer": "1134"
                                          }
                                        }
                                      ]
                                    }
                                  }
                                }
                                """
                            )
                    )
            )

        wiremockServer.start()
    }
}
