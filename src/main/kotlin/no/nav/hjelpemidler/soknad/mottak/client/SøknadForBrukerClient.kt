package no.nav.hjelpemidler.soknad.mottak.client

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.kittinunf.fuel.core.ResponseDeserializable
import com.github.kittinunf.fuel.coroutines.awaitObjectResponse
import com.github.kittinunf.fuel.httpGet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import no.nav.hjelpemidler.soknad.mottak.service.SoknadMedStatus
import no.nav.hjelpemidler.soknad.mottak.service.Status
import no.nav.hjelpemidler.soknad.mottak.service.SøknadForBrukerDto
import no.nav.hjelpemidler.soknad.mottak.tokenx.AccessToken
import no.nav.hjelpemidler.soknad.mottak.tokenx.TokendingsServiceWrapper
import java.util.Date
import java.util.UUID

private val logger = KotlinLogging.logger {}

internal interface SøknadForBrukerClient {

    suspend fun hentSoknad(soknadsId: UUID, tokenForExchange: String): SøknadForBrukerDto?
    suspend fun hentSoknaderForBruker(fnrBruker: String, tokenForExchange: String): List<SoknadMedStatus>
}

internal class SøknadForBrukerClientImpl(
    private val baseUrl: String,
    private val tokendingsWrapper: TokendingsServiceWrapper
) : SøknadForBrukerClient {

    override suspend fun hentSoknad(soknadsId: UUID, tokenForExchange: String): SøknadForBrukerDto? {
        return withContext(Dispatchers.IO) {

            kotlin.runCatching {

                "$baseUrl/soknad/bruker/$soknadsId".httpGet()
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("Authorization", "Bearer ${exchangeToken(tokenForExchange).value}")
                    .header("X-Correlation-ID", UUID.randomUUID().toString())
                    .awaitObjectResponse(
                        object : ResponseDeserializable<SøknadForBrukerDto> {
                            override fun deserialize(content: String): SøknadForBrukerDto {
                                return ObjectMapper().readValue(content, SøknadForBrukerDto::class.java)
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

    override suspend fun hentSoknaderForBruker(fnrBruker: String, tokenForExchange: String): List<SoknadMedStatus> {
        return withContext(Dispatchers.IO) {

            SoknadMedStatus(UUID.randomUUID(), Date(), Date(), Status.UTLØPT, true, "")
            kotlin.runCatching {

                "$baseUrl/soknad/bruker".httpGet()
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("Authorization", "Bearer ${exchangeToken(tokenForExchange).value}")
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

    private suspend fun exchangeToken(tokenForExchange: String): AccessToken {
        return tokendingsWrapper.exchangeTokenForSoknadsbehandlingDb(tokenForExchange)
    }
}
