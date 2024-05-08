package no.nav.hjelpemidler.soknad.mottak.client

import com.fasterxml.jackson.databind.JsonNode
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.accept
import io.ktor.client.request.header
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

private val logger = KotlinLogging.logger {}

class PdlClient(
    private val baseUrl: String,
    private val tokenSetProvider: TokenSetProvider,
) {
    private val httpClient: HttpClient = httpClient {
        openID(tokenSetProvider)
        defaultRequest {
            accept(ContentType.Application.Json)
            contentType(ContentType.Application.Json)
            correlationId()
            header("behandlingsnummer", "B735")
        }
    }

    suspend fun hentKommunenr(fnrBruker: String): String? {
        val body = KommunenrQuery(query = hentKommunenrQuery, variables = mapOf("ident" to fnrBruker))
        val jsonNode = withContext(Dispatchers.IO) {
            runCatching {
                httpClient.post(baseUrl) { setBody(body) }.body<JsonNode>()
            }.onSuccess {
                if (it.has("errors")) {
                    error("Feil ved henting av personinformasjon fra PDL ${it.get("errors")}")
                }
            }.onFailure {
                logger.error("Feil ved kall til PDL ${it.message}", it)
                throw it
            }.getOrThrow()
        }
        return jsonNode["data"].get("hentPerson")?.get("bostedsadresse")?.firstOrNull()?.get("vegadresse")
            ?.get("kommunenummer")?.textValue()
    }
}

data class KommunenrQuery(val query: String, val variables: Map<String, String>)

private val hentKommunenrQuery =
    """
        query(${'$'}ident: ID!) {
            hentPerson(ident: ${'$'}ident) {
                bostedsadresse(historikk: false ) {
                    vegadresse {
                        kommunenummer
                    }
                }
            }
        }
    """.trimIndent()
