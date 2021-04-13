package no.nav.hjelpemidler.soknad.mottak.client

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.kittinunf.fuel.core.ResponseDeserializable
import com.github.kittinunf.fuel.coroutines.awaitObjectResponse
import com.github.kittinunf.fuel.httpGet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import no.nav.hjelpemidler.soknad.mottak.db.SoknadForFormidler
import no.nav.hjelpemidler.soknad.mottak.tokenx.TokendingsServiceWrapper
import no.nav.personbruker.minesaker.api.tokenx.AccessToken
import no.nav.tms.token.support.idporten.user.IdportenUser
import java.util.UUID

private val logger = KotlinLogging.logger {}

internal interface SøknadForFormidlerClient {

    suspend fun hentSøknaderForFormidler(fnrFormidler: String, uker: Int, user: IdportenUser): List<SoknadForFormidler>
}

internal class SøknadForFormidlerClientImpl(
    private val baseUrl: String,
    private val tokendingsWrapper: TokendingsServiceWrapper
) : SøknadForFormidlerClient {

    override suspend fun hentSøknaderForFormidler(fnrFormidler: String, uker: Int, user: IdportenUser): List<SoknadForFormidler> {
        return withContext(Dispatchers.IO) {
            kotlin.runCatching {

                "$baseUrl/soknad/formidler".httpGet()
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("Authorization", "Bearer ${exchangeToken(user).value}")
                    .header("X-Correlation-ID", UUID.randomUUID().toString())
                    .awaitObjectResponse(
                        object : ResponseDeserializable<List<SoknadForFormidler>> {
                            override fun deserialize(content: String): List<SoknadForFormidler> {
                                return ObjectMapper().readValue(content, Array<SoknadForFormidler>::class.java).toList()
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

    private suspend fun exchangeToken(user: IdportenUser): AccessToken {
        return tokendingsWrapper.exchangeTokenForSoknadsbehandlingDb(user.tokenString)
    }
}
