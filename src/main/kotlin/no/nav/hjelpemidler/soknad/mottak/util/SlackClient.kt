package no.nav.hjelpemidler.soknad.mottak.util

import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.accept
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import no.nav.hjelpemidler.soknad.mottak.Configuration
import no.nav.hjelpemidler.soknad.mottak.httpClient

object SlackClient {
    private const val GRENSE_FOR_LANG_DOKUMENTBESKRIVELSE = 150

    private const val channel = "#digihot-brukers-hjelpemiddelside-dev"
    private const val username = "hm-soknadsbehandling"

    private val slackIntegrationEnabled = Configuration.slack.postDokumentbeskrivelseToSlack.toBoolean()
    private val environment = Configuration.slack.environment
    private val hookUrl = Configuration.slack.slackHook

    private val client = httpClient {
        defaultRequest {
            accept(ContentType.Application.Json)
            contentType(ContentType.Application.Json)
        }
    }

    fun post(message: String) {
        if (!slackIntegrationEnabled || message.length <= GRENSE_FOR_LANG_DOKUMENTBESKRIVELSE) return
        try {
            val values = mapOf(
                "text" to "${environment.uppercase()} - Dokumentbeskrivelse ble over $GRENSE_FOR_LANG_DOKUMENTBESKRIVELSE tegn: $message",
                "channel" to channel,
                "username" to username,
            )
            runBlocking(Dispatchers.IO) { client.post(hookUrl) { setBody(values) } }
        } catch (e: Exception) {
            log.warn("Posting av dokumentbeskrivelse feilet.", e)
        }
    }

    private val log = KotlinLogging.logger {}
}
