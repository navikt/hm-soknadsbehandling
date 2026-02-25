package no.nav.hjelpemidler.soknad.mottak.soknadapi

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.hjelpemidler.behovsmeldingsmodell.BehovsmeldingId
import no.nav.hjelpemidler.behovsmeldingsmodell.v2.Innsenderbehovsmelding
import no.nav.hjelpemidler.soknad.mottak.client.SøknadApiClient

private val log = KotlinLogging.logger {}

class SøknadApiService(
    private val søknadApiClient: SøknadApiClient
) {
    suspend fun genererNyPdf(behovsmeldingId: BehovsmeldingId, behovsmelding: Innsenderbehovsmelding) {
        log.info { "Genererer ny PDF for behovsmelding $behovsmeldingId" }
        søknadApiClient.genererNyPdf(behovsmeldingId, behovsmelding)
    }
}