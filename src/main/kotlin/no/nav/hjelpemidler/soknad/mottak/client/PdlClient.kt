package no.nav.hjelpemidler.soknad.mottak.client

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.github.kittinunf.fuel.core.Request
import com.github.kittinunf.fuel.core.ResponseDeserializable
import com.github.kittinunf.fuel.core.extensions.jsonBody
import com.github.kittinunf.fuel.coroutines.awaitObject
import com.github.kittinunf.fuel.httpPost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import no.nav.hjelpemidler.soknad.mottak.JacksonMapper
import no.nav.hjelpemidler.soknad.mottak.aad.AzureClient
import java.util.UUID

private val logger = KotlinLogging.logger {}

internal class PdlClient(
    private val azureClient: AzureClient,
    private val baseUrl: String,
    private val accesstokenScope: String
) {

    suspend fun hentKommunenr(fnrBruker: String): String? {
        val body = KommunenrQuery(query = hentKommunenrQuery, variables = mapOf("ident" to fnrBruker))

        val jsonNode = withContext(Dispatchers.IO) {
            kotlin.runCatching {
                baseUrl.httpPost()
                    .headers()
                    .jsonBody(JacksonMapper.objectMapper.writeValueAsString(body))
                    .awaitObject(
                        object : ResponseDeserializable<JsonNode> {
                            override fun deserialize(content: String): JsonNode {
                                return ObjectMapper().readTree(content)
                            }
                        }
                    )
            }
                .onSuccess {
                    if (it.has("errors")) {
                        throw RuntimeException("Feil ved henting av personinformasjon fra PDL ${it.get("errors")}")
                    }
                }
                .onFailure {
                    logger.error("Feil ved kall til PDL ${it.message}", it)
                    throw it
                }
                .getOrThrow()
        }
        return jsonNode["data"].get("hentPerson")?.get("bostedsadresse")?.firstOrNull()?.get("vegadresse")
            ?.get("kommunenummer")?.textValue()
    }

    private fun Request.headers() = this.header(
        mapOf(
            "Content-Type" to "application/json",
            "Accept" to "application/json",
            "Tema" to "HJE",
            "Authorization" to "Bearer ${azureClient.getToken(accesstokenScope).accessToken}",
            "X-Correlation-ID" to UUID.randomUUID().toString()
        )
    )
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
