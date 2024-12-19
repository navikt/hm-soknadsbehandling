package no.nav.hjelpemidler.soknad.mottak.client

import com.fasterxml.jackson.annotation.JsonProperty
import io.github.oshai.kotlinlogging.KotlinLogging
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
import no.nav.hjelpemidler.http.correlationId
import no.nav.hjelpemidler.http.openid.TokenSetProvider
import no.nav.hjelpemidler.http.openid.openID
import no.nav.hjelpemidler.soknad.mottak.httpClient
import java.time.LocalDate

private val log = KotlinLogging.logger {}

class InfotrygdProxyClient(
    private val baseUrl: String,
    private val tokenSetProvider: TokenSetProvider,
) {
    private val httpClient: HttpClient = httpClient {
        openID(tokenSetProvider)
        defaultRequest {
            accept(ContentType.Application.Json)
            contentType(ContentType.Application.Json)
            correlationId()
        }
    }

    suspend fun harVedtakFor(fnr: String, saksblokk: String, saksnr: String, vedtaksdato: LocalDate): Boolean {
        data class Request(
            val fnr: String,
            val saksblokk: String,
            val saksnr: String,
            @JsonProperty("vedtaksDato")
            val vedtaksdato: LocalDate,
        )

        data class Response(
            val resultat: Boolean,
        )

        return withContext(Dispatchers.IO) {
            runCatching {
                httpClient.post("$baseUrl/har-vedtak-for") {
                    setBody(Request(fnr, saksblokk, saksnr, vedtaksdato))
                }.body<Response>().resultat
            }.onFailure {
                log.error(it) { it.message }
            }.getOrDefault(false)
        }
    }
}
