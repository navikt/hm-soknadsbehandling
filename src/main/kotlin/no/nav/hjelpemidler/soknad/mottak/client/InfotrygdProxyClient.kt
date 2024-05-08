package no.nav.hjelpemidler.soknad.mottak.client

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.accept
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import no.nav.hjelpemidler.http.correlationId
import no.nav.hjelpemidler.http.openid.TokenSetProvider
import no.nav.hjelpemidler.http.openid.openID
import no.nav.hjelpemidler.soknad.mottak.httpClient
import java.time.LocalDate

private val logger = KotlinLogging.logger {}

interface InfotrygdProxyClient {
    suspend fun harVedtakFor(fnr: String, saksblokk: String, saksnr: String, vedtaksDato: LocalDate): Boolean
}

class InfotrygdProxyClientImpl(
    private val baseUrl: String,
    private val tokenSetProvider: TokenSetProvider,
) : InfotrygdProxyClient {
    private val httpClient: HttpClient = httpClient {
        openID(tokenSetProvider)
        defaultRequest {
            accept(ContentType.Application.Json)
            contentType(ContentType.Application.Json)
            correlationId()
        }
    }

    override suspend fun harVedtakFor(fnr: String, saksblokk: String, saksnr: String, vedtaksDato: LocalDate): Boolean {
        data class Request(
            val fnr: String,
            val saksblokk: String,
            val saksnr: String,
            val vedtaksDato: LocalDate,
        )

        data class Response(
            val resultat: Boolean,
        )

        return withContext(Dispatchers.IO) {
            runCatching {
                httpClient.post("$baseUrl/har-vedtak-for") {
                    setBody(Request(fnr, saksblokk, saksnr, vedtaksDato))
                }.body<Response>().resultat
            }.onFailure {
                logger.error(it) { it.message }
            }.getOrDefault(false)
        }
    }
}
