package no.nav.hjelpemidler.soknad.mottak.river

import com.fasterxml.jackson.databind.JsonNode
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.engine.apache.Apache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import no.nav.hjelpemidler.behovsmeldingsmodell.BehovsmeldingType
import no.nav.hjelpemidler.configuration.Environment
import no.nav.hjelpemidler.http.slack.slack
import no.nav.hjelpemidler.http.slack.slackIconEmoji
import no.nav.hjelpemidler.serialization.jackson.value
import no.nav.hjelpemidler.soknad.mottak.Configuration
import no.nav.hjelpemidler.soknad.mottak.client.GrunndataClient

object AutoGenerateDocumentTitle {
    private const val GRENSE_FOR_LANG_DOKUMENTBESKRIVELSE = 150

    private val log = KotlinLogging.logger {}
    private val slackClient by lazy { slack(engine = Apache.create()) }

    private val slackIntegrationEnabled = Configuration.POST_DOKUMENTBESKRIVELSE_TO_SLACK.toBoolean()
    private val environment = Environment.current.toString()

    fun generateTitle(behovsmelding: JsonNode): String {
        val behovsmeldingType: BehovsmeldingType =
            behovsmelding["behovsmeldingType"]?.value() ?: BehovsmeldingType.SØKNAD

        if (Environment.current.tier.isLocal) {
            return defaultTitle(behovsmeldingType)
        }

        val hmsnrs = when (behovsmeldingType) {
            BehovsmeldingType.BRUKERPASSBYTTE -> setOf(behovsmelding["brukerpassbytte"]["hjelpemiddel"]["artnr"].textValue())
            else -> behovsmelding["soknad"]["hjelpemidler"]["hjelpemiddelListe"]
                .mapNotNull { it["hmsNr"].asText() }
                .filter { it.isNotEmpty() }.toSet()
        }

        return generateTitle(hmsnrs, behovsmeldingType)
    }

    private fun generateTitle(hmsnrs: Set<String>, behovsmeldingType: BehovsmeldingType): String {
        val isokategorier = runCatching {
            runBlocking(Dispatchers.IO) {
                GrunndataClient.hentProdukter(hmsnrs)
            }
        }.getOrElse {
            // Let us default to defaultTitle(..) rather than throw if grunndata-search is down
            return defaultTitle(behovsmeldingType)
        }

        val title = isokategorier
            .asSequence()
            .mapNotNull { it.isoCategoryTitleShort }
            .filter { it.isNotEmpty() }
            .toSet()
            .sorted()
            .joinToString(separator = ", ")
            .lowercase()

        if (title.isEmpty()) {
            return defaultTitle(behovsmeldingType)
        }

        postToSlack(title)

        return when (behovsmeldingType) {
            BehovsmeldingType.BESTILLING -> "Bestilling av: $title"
            BehovsmeldingType.SØKNAD -> "Søknad om: $title"
            BehovsmeldingType.BYTTE, BehovsmeldingType.BRUKERPASSBYTTE -> "Bytte av: $title"
        }
    }

    private fun postToSlack(title: String) {
        if (!slackIntegrationEnabled || title.length <= GRENSE_FOR_LANG_DOKUMENTBESKRIVELSE) return
        runBlocking(Dispatchers.IO) {
            try {
                slackClient.sendMessage(
                    username = "hm-soknadsbehandling",
                    icon = slackIconEmoji(":this-is-fine-fire:"),
                    channel = "#digihot-brukers-hjelpemiddelside-dev",
                    message = "$environment - Dokumentbeskrivelsen ble over $GRENSE_FOR_LANG_DOKUMENTBESKRIVELSE tegn: $title"
                )
            } catch (e: Exception) {
                log.warn(e) { "Posting av dokumentbeskrivelse til Slack feilet" }
            }
        }
    }

    private fun defaultTitle(behovsmeldingType: BehovsmeldingType) =
        when (behovsmeldingType) {
            BehovsmeldingType.BESTILLING -> "Bestilling av hjelpemidler"
            BehovsmeldingType.SØKNAD -> "Søknad om hjelpemidler"
            BehovsmeldingType.BYTTE, BehovsmeldingType.BRUKERPASSBYTTE -> "Bytte av hjelpemidler"
        }
}
