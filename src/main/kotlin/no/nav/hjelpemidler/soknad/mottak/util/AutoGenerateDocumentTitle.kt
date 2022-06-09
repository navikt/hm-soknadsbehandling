package no.nav.hjelpemidler.soknad.mottak.util

import com.fasterxml.jackson.databind.JsonNode
import kotlinx.coroutines.runBlocking
import no.nav.hjelpemidler.soknad.mottak.Configuration
import no.nav.hjelpemidler.soknad.mottak.Profile
import no.nav.hjelpemidler.soknad.mottak.client.hmdb.HjelpemiddeldatabaseClient
import no.nav.hjelpemidler.soknad.mottak.util.slack.PostToSlack

object AutoGenerateDocumentTitle {
    private const val defaultTitleSoknad = "Søknad om hjelpemidler"
    private const val defaultTitleBestilling = "Bestilling av hjelpemidler"
    private const val bestillingPrefix = "Bestilling av:"
    private const val soknadPrefix = "Søknad om:"

    fun generateTitle(soknad: JsonNode): String {
        val behovsmeldingsType = soknad["behovsmeldingType"]?.textValue()
        val erBestilling = behovsmeldingsType == "BESTILLING"

        if (Configuration.application.profile == Profile.LOCAL) {
            return when (erBestilling) {
                true -> defaultTitleBestilling
                false -> defaultTitleSoknad
            }
        }

        val hjelpemidlerListe = soknad["soknad"]["hjelpemidler"]["hjelpemiddelListe"]
        val hmsnrs = hjelpemidlerListe.mapNotNull { it["hmsNr"].asText() }.filter { it.isNotEmpty() }.toSet()
        val isokategorier = runBlocking {
            HjelpemiddeldatabaseClient.hentIsokortnavnForProdukterMedHmsnrs(hmsnrs)
        }

        val title = isokategorier
            .mapNotNull { it.isokortnavn }
            .filter { it.isNotEmpty() }
            .toSet()
            .sorted()
            .joinToString(separator = ", ")
            .lowercase()

        if (title.isEmpty()) {
            return when (erBestilling) {
                true -> defaultTitleBestilling
                false -> defaultTitleSoknad
            }
        }
        PostToSlack().post(title)
        return when (erBestilling) {
            true -> "$bestillingPrefix $title"
            false -> "$soknadPrefix $title"
        }
    }
}
