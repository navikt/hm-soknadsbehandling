package no.nav.hjelpemidler.soknad.mottak.client

import com.fasterxml.jackson.databind.JsonNode
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
import java.util.UUID

private val logger = KotlinLogging.logger {}

internal class PdlClient(
    private val azureClient: AzureClient,
    private val baseUrl: String,
    private val accesstokenScope: String,
    private val httpClient: HttpClient = httpClient()
) {

    suspend fun hentKommunenr(fnrBruker: String): String? {
        val body = KommunenrQuery(query = hentKommunenrQuery, variables = mapOf("ident" to fnrBruker))

        val jsonNode = withContext(Dispatchers.IO) {
            kotlin.runCatching {
                httpClient.request(baseUrl) {
                    method = HttpMethod.Post
                    headers {
                        contentType(ContentType.Application.Json)
                        accept(ContentType.Application.Json)
                        header("behandlingsnummer", "B735")
                        header("Authorization", "Bearer ${azureClient.getToken(accesstokenScope).accessToken}")
                        header("X-Correlation-ID", UUID.randomUUID().toString())
                    }
                    setBody(body)
                }.body<JsonNode>()
            }.onSuccess {
                if (it.has("errors")) {
                    throw RuntimeException("Feil ved henting av personinformasjon fra PDL ${it.get("errors")}")
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

internal data class KommunenrQuery(val query: String, val variables: Map<String, String>)

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
