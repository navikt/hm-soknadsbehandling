package no.nav.hjelpemidler.soknad.mottak.delbestilling

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.accept
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.nav.hjelpemidler.http.correlationId
import no.nav.hjelpemidler.http.openid.TokenSetProvider
import no.nav.hjelpemidler.http.openid.openID
import no.nav.hjelpemidler.soknad.mottak.httpClient
import java.time.LocalDate

private val log = KotlinLogging.logger {}

class DelbestillingClient(
    private val baseUrl: String,
    private val tokenSetProvider: TokenSetProvider,
) {
    private val httpClient: HttpClient = httpClient {
        openID(tokenSetProvider)
        defaultRequest {
            accept(ContentType.Application.Json)
            contentType(ContentType.Application.Json)
            correlationId()
        }
    }

    suspend fun oppdaterStatus(delbestillingId: String, status: Status, ordrenummer: String) {
        return withContext(Dispatchers.IO) {
            try {
                httpClient.put("$baseUrl/delbestilling/status/v2/$delbestillingId") {
                    setBody(StatusOppdateringDto(status, ordrenummer))
                }
            } catch (e: Exception) {
                log.error(e) { "Request for å oppdatere status for delbestillingId: $delbestillingId feilet" }
                throw e
            }
        }
    }

    suspend fun oppdaterDellinjeStatus(ordrenummer: Int, status: Status, hmsnr: String, datoOppdatert: LocalDate) {
        return withContext(Dispatchers.IO) {
            try {
                httpClient.put("$baseUrl/delbestilling/status/dellinje/$ordrenummer") {
                    setBody(DellinjeStatusOppdateringDto(status, hmsnr, datoOppdatert))
                }
            } catch (e: Exception) {
                log.error(e) { "Request for å skipningsbekrefte dellinje feilet, ordrenr: $ordrenummer, hmsnr: $hmsnr." }
                throw e
            }
        }
    }
}

private data class StatusOppdateringDto(
    val status: Status,
    val oebsOrdrenummer: String,
)

private data class DellinjeStatusOppdateringDto(
    val status: Status,
    val hmsnr: String,
    val datoOppdatert: LocalDate,
)
