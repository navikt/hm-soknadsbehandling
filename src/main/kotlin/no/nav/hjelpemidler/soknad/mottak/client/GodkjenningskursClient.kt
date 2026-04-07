package no.nav.hjelpemidler.soknad.mottak.client

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.accept
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.nav.hjelpemidler.domain.person.Fødselsnummer
import no.nav.hjelpemidler.domain.person.Personnavn
import no.nav.hjelpemidler.http.correlationId
import no.nav.hjelpemidler.http.openid.TokenSetProvider
import no.nav.hjelpemidler.http.openid.openID
import no.nav.hjelpemidler.soknad.mottak.httpClient

private val log = KotlinLogging.logger {}

class GodkjenningskursClient(
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

    suspend fun oppdaterPersoninfo(personinfo: Personinfo) {
        return withContext(Dispatchers.IO) {
            try {
                httpClient.put("$baseUrl/personinfo") {
                    setBody(personinfo)
                }
            } catch (e: Exception) {
                log.error(e) { "Request for å oppdatere personinfo for godkjenningskurs feilet" }
                throw e
            }
        }
    }
}

data class Personinfo(
    val fnr: Fødselsnummer,
    val navn: Personnavn,
    val epost: String,
    val arbeidssted: String,
    val kommunenummer: String,
)
