package no.nav.hjelpemidler.soknad.mottak.delbestilling

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.header
import io.ktor.client.request.headers
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpMessageBuilder
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import no.nav.hjelpemidler.soknad.mottak.aad.AzureClient
import no.nav.hjelpemidler.soknad.mottak.httpClient
import java.util.UUID

private val logger = KotlinLogging.logger {}

internal class DelbestillingClient(
    private val baseUrl: String,
    private val azureClient: AzureClient,
    private val accesstokenScope: String,
    private val httpClient: HttpClient = httpClient(),
) {

    suspend fun oppdaterStatus(delbestillingId: String, status: Status): Int {
        return withContext(Dispatchers.IO) {
            kotlin.runCatching {
                httpClient.request("$baseUrl/delbestilling/status/$delbestillingId") {
                    method = HttpMethod.Put
                    headers()
                    setBody(status)
                }.body<Int>()
            }.onFailure {
                logger.error { it.message }
            }.getOrThrow()
        }
    }

    private fun HttpMessageBuilder.headers() = this.headers {
        contentType(ContentType.Application.Json)
        accept(ContentType.Application.Json)
        header("Authorization", "Bearer ${azureClient.getToken(accesstokenScope).accessToken}")
        header("X-Correlation-ID", UUID.randomUUID().toString())
    }
}
