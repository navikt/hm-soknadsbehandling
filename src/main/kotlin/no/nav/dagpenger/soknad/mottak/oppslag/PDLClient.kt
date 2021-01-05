package no.nav.dagpenger.soknad.mottak.oppslag

import com.fasterxml.jackson.databind.JsonNode
import com.github.kittinunf.fuel.core.ResponseDeserializable
import com.github.kittinunf.fuel.core.extensions.jsonBody
import com.github.kittinunf.fuel.coroutines.awaitObject
import com.github.kittinunf.fuel.httpPost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import no.nav.dagpenger.soknad.mottak.serder.ObjectMapper

private val logger = KotlinLogging.logger {}

internal class PDLClient(private val baseUrl: String, private val stsClient: StsClient) {
    companion object {
        const val PATH = "graphql"
        fun aktorQuery(fnr: String) =
            """
        {
            "query": "query(${'$'}ident: ID!) { hentIdenter(ident:${'$'}ident, grupper: [AKTORID]) { identer { ident,gruppe, historisk } } }",
            "variables": {
                "ident": "$fnr"
            }
        }            
        """
    }

    suspend fun getAktorId(fnr: String): String {
        logger.info { "Slår opp ident for person" }

        return executeQuery<String>(aktorQuery(fnr)) {
            this["data"]["hentIdenter"]["identer"][0]["ident"].asText()
        }
    }

    private suspend fun <T : Any> executeQuery(jsonQuery: String, deserialize: JsonNode.() -> T): T {
        return withContext(Dispatchers.IO) {
            kotlin.runCatching {
                val token = stsClient.getToken()
                "$baseUrl/$PATH".httpPost()
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("Nav-Consumer-Token", "Bearer $token")
                    .header("Authorization", "Bearer $token")
                    .header("TEMA", "DAG")
                    .jsonBody(jsonQuery)
                    .awaitObject(
                        object : ResponseDeserializable<JsonNode> {
                            override fun deserialize(content: String): JsonNode {
                                return ObjectMapper.instance.readTree(content)
                            }
                        }
                    )
                    .let {
                        when (it.hasNonNull("errors")) {
                            true -> throw PdlException(it["errors"].toString())
                            else -> it.deserialize()
                        }
                    }
            }
                .onFailure { logger.error { it.message } }
                .getOrThrow()
        }
    }
}

internal class PdlException(msg: String) : RuntimeException(msg)
