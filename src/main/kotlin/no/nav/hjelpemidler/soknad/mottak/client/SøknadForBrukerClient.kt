package no.nav.hjelpemidler.soknad.mottak.client

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.kittinunf.fuel.core.ResponseDeserializable
import com.github.kittinunf.fuel.coroutines.awaitObjectResponse
import com.github.kittinunf.fuel.httpGet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import no.nav.hjelpemidler.soknad.mottak.service.SoknadMedStatus
import no.nav.hjelpemidler.soknad.mottak.service.SøknadForBruker
import no.nav.hjelpemidler.soknad.mottak.tokenx.TokendingsServiceWrapper
import no.nav.personbruker.minesaker.api.tokenx.AccessToken
import no.nav.tms.token.support.idporten.user.IdportenUser
import java.util.UUID

private val logger = KotlinLogging.logger {}

internal interface SøknadForBrukerClient {

    suspend fun hentSoknad(soknadsId: UUID, user: IdportenUser): SøknadForBruker?
    suspend fun hentSoknaderForBruker(fnrBruker: String, user: IdportenUser): List<SoknadMedStatus>
}

internal class SøknadForBrukerClientImpl(
    private val baseUrl: String,
    private val tokendingsWrapper: TokendingsServiceWrapper
) : SøknadForBrukerClient {

    override suspend fun hentSoknad(soknadsId: UUID, user: IdportenUser): SøknadForBruker? {
        return withContext(Dispatchers.IO) {
            kotlin.runCatching {

                "$baseUrl/soknad/bruker/$soknadsId".httpGet()
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("Authorization", "Bearer ${exchangeToken(user).value}")
                    .header("X-Correlation-ID", UUID.randomUUID().toString())
                    .awaitObjectResponse(
                        object : ResponseDeserializable<SøknadForBruker> {
                            override fun deserialize(content: String): SøknadForBruker {
                                return ObjectMapper().readValue(content, SøknadForBruker::class.java)
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

    override suspend fun hentSoknaderForBruker(fnrBruker: String, user: IdportenUser): List<SoknadMedStatus> {
        return withContext(Dispatchers.IO) {
            kotlin.runCatching {

                "$baseUrl/soknad/bruker".httpGet()
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("Authorization", "Bearer ${exchangeToken(user).value}")
                    .header("X-Correlation-ID", UUID.randomUUID().toString())
                    .awaitObjectResponse(
                        object : ResponseDeserializable<List<SoknadMedStatus>> {
                            override fun deserialize(content: String): List<SoknadMedStatus> {
                                return ObjectMapper().readValue(content, Array<SoknadMedStatus>::class.java).toList()
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
