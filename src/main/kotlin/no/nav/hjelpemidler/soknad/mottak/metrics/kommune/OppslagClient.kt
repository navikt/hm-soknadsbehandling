package no.nav.hjelpemidler.soknad.mottak.metrics.kommune

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.header
import io.ktor.client.request.headers
import io.ktor.client.request.request
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import no.nav.hjelpemidler.soknad.mottak.Configuration
import no.nav.hjelpemidler.soknad.mottak.httpClient
import java.time.LocalDateTime
import java.time.LocalDateTime.now
import java.util.UUID

private val logger = KotlinLogging.logger {}

interface OppslagClient {
    suspend fun hentAlleKommuner(): Map<String, KommuneDto>
}

class HttpOppslagClient(
    private val oppslagUrl: String = Configuration.oppslagUrl,
    private val httpClient: HttpClient = httpClient()
) : OppslagClient {
    override suspend fun hentAlleKommuner(): Map<String, KommuneDto> {
        val kommunenrUrl = "$oppslagUrl/geografi/kommunenr"
        logger.info("Henter alle kommuner fra $kommunenrUrl")

        return withContext(Dispatchers.IO) {
            kotlin.runCatching {
                httpClient.request(kommunenrUrl) {
                    method = HttpMethod.Get
                    headers {
                        accept(ContentType.Application.Json)
                        header("X-Correlation-ID", UUID.randomUUID().toString())
                    }
                }.body<Map<String, KommuneDto>>()
            }.onFailure {
                logger.error(it) { "Henting av kommune feilet: ${it.message}" }
            }
        }.getOrThrow()
    }
}

class CachedOppslagClient(val httpOppslagClient: HttpOppslagClient = HttpOppslagClient()) : OppslagClient {

    private var kommunerCache: Map<String, KommuneDto> = runBlocking { httpOppslagClient.hentAlleKommuner() }
    private var cacheExpiery: LocalDateTime = now().plusHours(1)

    override suspend fun hentAlleKommuner(): Map<String, KommuneDto> {
        if (cacheExpiery.isBefore(now())) {
            kommunerCache = httpOppslagClient.hentAlleKommuner()
            cacheExpiery = now().plusHours(1)
        }
        return kommunerCache
    }

}