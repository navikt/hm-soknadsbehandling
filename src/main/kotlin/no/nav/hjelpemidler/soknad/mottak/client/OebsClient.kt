package no.nav.hjelpemidler.soknad.mottak.client


import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpMethod
import mu.KotlinLogging
import no.nav.hjelpemidler.soknad.mottak.aad.AzureClient
import no.nav.hjelpemidler.soknad.mottak.httpClient

private val logg = KotlinLogging.logger {}
private val secureLogg = KotlinLogging.logger("tjenestekall")

class OebsClient(
    private val azureClient: AzureClient,
    private val baseUrl: String,
    private val apiScope: String,
    private val httpClient: HttpClient = httpClient()
) {
    suspend fun hentBrukerpassrollebytter(): List<Brukerpassrollebytter> {
        try {
            logg.info { "hentBrukerpassrollebytter()" }
            val httpResponse: HttpResponse = httpClient.request("$baseUrl/hent-brukerpassbytte-brukere") {
                method = HttpMethod.Get
                header("Authorization", "Bearer ${azureClient.getToken(apiScope).accessToken}")
                header("Accept", "application/json")
            }
            return httpResponse.body()
        } catch (e: Exception) {
            logg.error(e) { "Kunne ikke hente brukerpassrollebytter" }
            throw e
        }
    }
}


data class Brukerpassrollebytter(
    val fnr: String,
    val utlånsType: String?,
    val innleveringsDato: String?,
    val oppdatertInnleveringsDato: String?,
    val kanByttes: Boolean,
) {
}