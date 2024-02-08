package no.nav.hjelpemidler.soknad.mottak.util

import com.fasterxml.jackson.databind.JsonNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import no.nav.hjelpemidler.soknad.mottak.Configuration
import no.nav.hjelpemidler.soknad.mottak.Profile
import no.nav.hjelpemidler.soknad.mottak.asObject
import no.nav.hjelpemidler.soknad.mottak.client.hmdb.HjelpemiddeldatabaseClient
import no.nav.hjelpemidler.soknad.mottak.util.slack.PostToSlack

private val log = KotlinLogging.logger {}

object AutoGenerateDocumentTitle {

    fun generateTitle(soknad: JsonNode): String {
        val behovsmeldingType: Behovsmeldingtype = soknad["behovsmeldingType"]?.asObject() ?: Behovsmeldingtype.SØKNAD

        if (Configuration.application.profile == Profile.LOCAL) {
            return defaultTitle(behovsmeldingType)
        }

        val hjelpemidlerListe = soknad["soknad"]["hjelpemidler"]["hjelpemiddelListe"]
        val hmsnrs = hjelpemidlerListe.mapNotNull { it["hmsNr"].asText() }.filter { it.isNotEmpty() }.toSet()
        val isokategorier = runBlocking(Dispatchers.IO) {
            HjelpemiddeldatabaseClient.hentProdukter(hmsnrs)
        }

        val title = isokategorier
            .mapNotNull { it.isoCategoryTextShort }
            .filter { it.isNotEmpty() }
            .toSet()
            .sorted()
            .joinToString(separator = ", ")
            .lowercase()

        if (title.isEmpty()) {
            return defaultTitle(behovsmeldingType)
        }
        PostToSlack().post(title)
        return when (behovsmeldingType) {
            Behovsmeldingtype.BESTILLING -> "Bestilling av: $title"
            Behovsmeldingtype.SØKNAD -> "Søknad om: $title"
            Behovsmeldingtype.BYTTE -> "Bytte av: $title"
        }
    }

    private fun defaultTitle(behovsmeldingtype: Behovsmeldingtype) =
        when (behovsmeldingtype) {
            Behovsmeldingtype.BESTILLING -> "Bestilling av hjelpemidler"
            Behovsmeldingtype.SØKNAD -> "Søknad om hjelpemidler"
            Behovsmeldingtype.BYTTE -> "Bytte av hjelpemidler"
        }

    private enum class Behovsmeldingtype {
        SØKNAD, BESTILLING, BYTTE
    }
}
