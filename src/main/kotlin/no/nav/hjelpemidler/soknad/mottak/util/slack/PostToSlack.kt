package no.nav.hjelpemidler.soknad.mottak.util.slack

import com.fasterxml.jackson.databind.ObjectMapper
import no.nav.hjelpemidler.soknad.mottak.Configuration
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

private val log = LoggerFactory.getLogger("PostToSlack")

class PostToSlack {
    private val username = "hm-soknadsbehandling"
    private val slackIntegrationEnabled = Configuration.slack.postDokumentbeskrivelseToSlack.toBoolean()
    private val environment = Configuration.slack.environment
    private val hookUrl = Configuration.slack.slackHook
    private val channel = "#digihot-brukers-hjelpemiddelside-dev"
    private val GRENSE_FOR_LANG_DOKUMENTBESKRIVELSE = 100

    fun post(message: String) {
        if (slackIntegrationEnabled && message.length > GRENSE_FOR_LANG_DOKUMENTBESKRIVELSE) {
            try {
                val slackMessage = "${environment.uppercase()} - Dokumentbeskrivelse ble over $GRENSE_FOR_LANG_DOKUMENTBESKRIVELSE tegn: $message"
                val values = mapOf(
                    "text" to slackMessage,
                    "channel" to channel,
                    "username" to username,
                )

                val objectMapper = ObjectMapper()
                val requestBody: String = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(values)

                val client = HttpClient.newBuilder().build()
                val request = HttpRequest.newBuilder()
                    .uri(URI.create(hookUrl))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build()
                client.send(request, HttpResponse.BodyHandlers.ofString())
            } catch (e: Exception) {
                log.warn("Posting av dokumentbeskrivelse feilet.", e)
            }
        }
    }
}
