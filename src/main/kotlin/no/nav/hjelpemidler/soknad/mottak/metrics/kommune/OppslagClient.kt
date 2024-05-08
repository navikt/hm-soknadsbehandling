package no.nav.hjelpemidler.soknad.mottak.metrics.kommune

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.http.ContentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import no.nav.hjelpemidler.http.correlationId
import no.nav.hjelpemidler.soknad.mottak.Configuration
import no.nav.hjelpemidler.soknad.mottak.httpClient
import java.time.LocalDateTime

private val logger = KotlinLogging.logger {}

interface OppslagClient {
    suspend fun hentAlleKommuner(): Map<String, KommuneDto>
}

class OppslagClientImpl(
    private val oppslagUrl: String = Configuration.oppslagUrl,
) : OppslagClient {
    private val httpClient: HttpClient = httpClient {
        defaultRequest {
            accept(ContentType.Application.Json)
            correlationId()
        }
    }

    override suspend fun hentAlleKommuner(): Map<String, KommuneDto> {
        val kommunenrUrl = "$oppslagUrl/geografi/kommunenr"
        logger.info("Henter alle kommuner fra $kommunenrUrl")
        return withContext(Dispatchers.IO) {
            runCatching {
                httpClient.get(kommunenrUrl).body<Map<String, KommuneDto>>()
            }.onFailure {
                logger.error(it) { "Henting av kommune feilet: ${it.message}" }
            }
        }.getOrThrow()
    }
}

class CachedOppslagClient(val oppslagClient: OppslagClient = OppslagClientImpl()) : OppslagClient {
    private var kommunerCache: Map<String, KommuneDto> = runBlocking(Dispatchers.IO) {
        oppslagClient.hentAlleKommuner()
    }
    private var cacheExpiry: LocalDateTime = LocalDateTime.now().plusHours(1)

    override suspend fun hentAlleKommuner(): Map<String, KommuneDto> {
        if (cacheExpiry.isBefore(LocalDateTime.now())) {
            kommunerCache = oppslagClient.hentAlleKommuner()
            cacheExpiry = LocalDateTime.now().plusHours(1)
        }
        return kommunerCache
    }
}
