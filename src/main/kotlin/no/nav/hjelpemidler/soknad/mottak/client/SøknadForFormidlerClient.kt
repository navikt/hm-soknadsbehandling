package no.nav.hjelpemidler.soknad.mottak.client

import com.github.kittinunf.fuel.core.ResponseDeserializable
import com.github.kittinunf.fuel.coroutines.awaitObjectResponse
import com.github.kittinunf.fuel.httpGet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import no.nav.hjelpemidler.soknad.mottak.JacksonMapper
import no.nav.hjelpemidler.soknad.mottak.tokenx.AccessToken
import no.nav.hjelpemidler.soknad.mottak.tokenx.TokendingsServiceWrapper
import java.util.UUID

private val logger = KotlinLogging.logger {}

internal interface SøknadForFormidlerClient {

    suspend fun hentSøknaderForFormidler(fnrFormidler: String, uker: Int, tokenForExchange: String): List<SoknadForFormidler>
}

internal class SøknadForFormidlerClientImpl(
    private val baseUrl: String,
    private val tokendingsWrapper: TokendingsServiceWrapper
) : SøknadForFormidlerClient {

    override suspend fun hentSøknaderForFormidler(fnrFormidler: String, uker: Int, tokenForExchange: String): List<SoknadForFormidler> {
        return withContext(Dispatchers.IO) {
            kotlin.runCatching {

                "$baseUrl/soknad/formidler".httpGet()
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("Authorization", "Bearer ${exchangeToken(tokenForExchange).value}")
                    .header("X-Correlation-ID", UUID.randomUUID().toString())
                    .awaitObjectResponse(
                        object : ResponseDeserializable<List<SoknadForFormidler>> {
                            override fun deserialize(content: String): List<SoknadForFormidler> {
                                return JacksonMapper.objectMapper.readValue(content, Array<SoknadForFormidler>::class.java).toList()
                            }
                        }
                    ).third
            }
                .onFailure {
                    logger.error { it.message }
                }
        }
            .getOrThrow()
    }

    private suspend fun exchangeToken(tokenForExchange: String): AccessToken {
        return tokendingsWrapper.exchangeTokenForSoknadsbehandlingDb(tokenForExchange)
    }
}
