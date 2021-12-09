package no.nav.hjelpemidler.soknad.mottak.util

import com.fasterxml.jackson.databind.JsonNode
import kotlinx.coroutines.runBlocking
import no.nav.hjelpemidler.soknad.mottak.Configuration
import no.nav.hjelpemidler.soknad.mottak.Profile
import no.nav.hjelpemidler.soknad.mottak.client.hmdb.HjelpemiddeldatabaseClient

object AutoGenerateDocumentTitle {

    fun generateTitle(soknad: JsonNode): String? {
        if (Configuration.application.profile == Profile.LOCAL) return "Søknad om hjelpemidler"

        val hjelpemidlerListe = soknad["soknad"]["hjelpemidler"]["hjelpemiddelListe"]
        val hmsnrs = hjelpemidlerListe.mapNotNull { it["hmsNr"].asText() }.filter { it.isNotEmpty() }.toSet()
        val isokategorier = runBlocking {
            HjelpemiddeldatabaseClient.hentIsokortnavnForProdukterMedHmsnrs(hmsnrs)
        }
        return isokategorier.mapNotNull { it.isokortnavn }.filter { it.isNotEmpty() }.toSet().joinToString(separator = "; ")

        /*
        val dokumentbeskrivelse = hjelpemidlerListe.mapNotNull {
            val isokode = soknad["produkt"]["isocode"].asLong()
            val kortnavn: String? =
                isokategorier.find { kategori -> kategori.isokode == isokode }?.kortnavn?.toLowerCase()
            kortnavn ?: it["produkt"]["isotitle"].asText().toLowerCase()
        }.toSet().joinToString(separator = "; ")
        */
    }
}
