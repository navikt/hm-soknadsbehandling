package no.nav.hjelpemidler.soknad.mottak.client


import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.apache.Apache
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpMethod
import io.ktor.serialization.jackson.jackson
import mu.KotlinLogging
import no.nav.hjelpemidler.soknad.mottak.aad.AzureClient

private val logg = KotlinLogging.logger {}
private val secureLogg = KotlinLogging.logger("tjenestekall")

private fun oebsHttpClient(): HttpClient = HttpClient(Apache) {
    expectSuccess = true
    install(ContentNegotiation) {
        jackson {
            registerModule(JavaTimeModule())
            disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        }
    }
    install(HttpTimeout) {
        requestTimeoutMillis = 5*60*1000
        socketTimeoutMillis = 5*60*1000

    }
}

class OebsClient(
    private val azureClient: AzureClient,
    private val baseUrl: String,
    private val apiScope: String,
    private val httpClient: HttpClient = oebsHttpClient()
) {
    suspend fun hentBrukerpassbrukere(): List<String> {
        try {
            val httpResponse: HttpResponse = httpClient.request("$baseUrl/brukerpassbrukere") {
                method = HttpMethod.Get
                header("Authorization", "Bearer ${azureClient.getToken(apiScope).accessToken}")
                header("Accept", "application/json")
            }
            return httpResponse.body()
        } catch (e: Exception) {
            logg.error(e) { "Kunne ikke hente brukerpassbrukere" }
            throw e
        }
    }

    suspend fun harGyldigBrukerpassbytteUtlån(fnr: String): BrukerpassbytteDto {
        try {
            val httpResponse: HttpResponse = httpClient.request("$baseUrl/har-gyldig-brukerpassbytte-utlaan") {
                method = HttpMethod.Post
                header("Authorization", "Bearer ${azureClient.getToken(apiScope).accessToken}")
                header("Accept", "application/json")
                setBody(FnrDto(fnr))
            }
            return httpResponse.body()
        } catch (e: Exception) {
            logg.error(e) { "Kunne ikke hente brukerpassrollebytter" }
            throw e
        }
    }
}


private data class FnrDto(
    val fnr: String
)

data class BrukerpassbytteDto(
    val harGyldigUtlån: Boolean
)
