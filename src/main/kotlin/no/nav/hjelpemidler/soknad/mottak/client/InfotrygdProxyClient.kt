package no.nav.hjelpemidler.soknad.mottak.client

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.header
import io.ktor.client.request.headers
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import no.nav.hjelpemidler.soknad.mottak.aad.AzureClient
import no.nav.hjelpemidler.soknad.mottak.httpClient
import java.time.LocalDate
import java.util.UUID

private val logger = KotlinLogging.logger {}

internal interface InfotrygdProxyClient {
    suspend fun harVedtakFor(fnr: String, saksblokk: String, saksnr: String, vedtaksDato: LocalDate): Boolean
}

internal class InfotrygdProxyClientImpl(
    private val baseUrl: String,
    private val azureClient: AzureClient,
    private val accesstokenScope: String,
    private val httpClient: HttpClient = httpClient()
) : InfotrygdProxyClient {

    override suspend fun harVedtakFor(fnr: String, saksblokk: String, saksnr: String, vedtaksDato: LocalDate): Boolean {
        data class Request(
            val fnr: String,
            val saksblokk: String,
            val saksnr: String,
            val vedtaksDato: LocalDate
        )

        data class Response(
            val resultat: Boolean
        )

        return withContext(Dispatchers.IO) {
            kotlin.runCatching {
                httpClient.request("$baseUrl/har-vedtak-for") {
                    method = HttpMethod.Post
                    headers {
                        contentType(ContentType.Application.Json)
                        accept(ContentType.Application.Json)
                        header("Authorization", "Bearer ${azureClient.getToken(accesstokenScope).accessToken}")
                        header("X-Correlation-ID", UUID.randomUUID().toString())
                    }
                    setBody(Request(fnr, saksblokk, saksnr, vedtaksDato))
                }.body<Response>().resultat
            }.onFailure {
                logger.error { it.message }
            }.getOrDefault(false)
        }
    }
}
