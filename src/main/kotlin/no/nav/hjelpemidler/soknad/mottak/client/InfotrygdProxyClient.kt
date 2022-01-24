package no.nav.hjelpemidler.soknad.mottak.client

import com.fasterxml.jackson.module.kotlin.readValue
import com.github.kittinunf.fuel.core.Request
import com.github.kittinunf.fuel.core.ResponseDeserializable
import com.github.kittinunf.fuel.core.extensions.jsonBody
import com.github.kittinunf.fuel.coroutines.awaitObject
import com.github.kittinunf.fuel.httpGet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import no.nav.hjelpemidler.soknad.mottak.JacksonMapper
import no.nav.hjelpemidler.soknad.mottak.aad.AzureClient
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
) : InfotrygdProxyClient {

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
            kotlin.runCatching {
                "$baseUrl/har-vedtak-for".httpGet()
                    .headers()
                    .jsonBody(
                        JacksonMapper.objectMapper.writeValueAsString(
                            Request(
                                fnr,
                                saksblokk,
                                saksnr,
                                vedtaksDato,
                            )
                        )
                    )
                    .awaitObject(
                        object : ResponseDeserializable<Response> {
                            override fun deserialize(content: String): Response {
                                return JacksonMapper.objectMapper.readValue(content)
                            }
                        }
                    ).resultat
            }.onFailure {
                logger.error { it.message }
            }.getOrDefault(false)
        }
    }

    private fun Request.headers() = this.header(
        mapOf(
            "Content-Type" to "application/json",
            "Accept" to "application/json",
            "Authorization" to "Bearer ${azureClient.getToken(accesstokenScope).accessToken}",
            "X-Correlation-ID" to UUID.randomUUID().toString()
        )
    )
}
