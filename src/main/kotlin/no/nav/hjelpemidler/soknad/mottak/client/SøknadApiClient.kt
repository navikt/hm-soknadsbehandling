package no.nav.hjelpemidler.soknad.mottak.client

import io.ktor.client.HttpClient
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.accept
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import no.nav.hjelpemidler.behovsmeldingsmodell.BehovsmeldingId
import no.nav.hjelpemidler.behovsmeldingsmodell.v2.Innsenderbehovsmelding
import no.nav.hjelpemidler.http.correlationId
import no.nav.hjelpemidler.http.openid.entraId
import no.nav.hjelpemidler.soknad.mottak.Configuration
import no.nav.hjelpemidler.soknad.mottak.httpClient
import java.io.Closeable

class SøknadApiClient(private val baseUrl: String = Configuration.SOKNAD_API_BASEURL) : Closeable {
    private val httpClient: HttpClient = httpClient {
        entraId(Configuration.SOKNAD_API_SCOPE)
        defaultRequest {
            accept(ContentType.Application.Json)
            contentType(ContentType.Application.Json)
            correlationId()
        }
    }

    suspend fun genererNyPdf(behovsmeldingId: BehovsmeldingId, behovsmelding: Innsenderbehovsmelding) {
        httpClient
            .post("$baseUrl/pdf/regenerer/$behovsmeldingId") {
                setBody(behovsmelding)
            }
    }

    override fun close() = httpClient.close()
}
