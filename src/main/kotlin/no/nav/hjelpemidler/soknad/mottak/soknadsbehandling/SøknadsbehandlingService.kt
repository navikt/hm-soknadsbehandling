package no.nav.hjelpemidler.soknad.mottak.soknadsbehandling

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.hjelpemidler.behovsmeldingsmodell.BehovsmeldingStatus
import no.nav.hjelpemidler.behovsmeldingsmodell.BehovsmeldingType
import no.nav.hjelpemidler.behovsmeldingsmodell.SøknadId
import no.nav.hjelpemidler.soknad.mottak.client.Søknad
import java.util.UUID
import no.nav.hjelpemidler.soknad.mottak.client.SøknadForRiverClient as SøknadsbehandlingClient

private val log = KotlinLogging.logger {}

class SøknadsbehandlingService(private val søknadsbehandlingClient: SøknadsbehandlingClient) {
    suspend fun hentSøknad(søknadId: SøknadId, inkluderData: Boolean = false): Søknad {
        log.info { "Henter søknad, søknadId: $søknadId, inkluderData: $inkluderData" }
        return søknadsbehandlingClient.hentSøknad(søknadId, inkluderData)
    }

    suspend fun hentBehovsmeldingstype(søknadId: SøknadId): BehovsmeldingType {
        return try {
            hentSøknad(søknadId).behovsmeldingstype
        } catch (e: Exception) {
            log.error(e) { "Feil ved henting av behovsmeldingstype for søknadId: $søknadId" }
            BehovsmeldingType.SØKNAD
        }
    }

    suspend fun oppdaterStatus(søknadId: SøknadId, statusendring: Statusendring): Boolean {
        val status = statusendring.status
        log.info { "Oppdaterer status på søknad, søknadId: $søknadId, status: $status" }
        val result = søknadsbehandlingClient.oppdaterStatus(søknadId, statusendring)
        val oppdatert = result > 0
        if (oppdatert) {
            log.info { "Status ble endret, søknadId: $søknadId, status: $status" }
        } else {
            log.warn { "Status ble ikke endret, søknadId: $søknadId, status: $status" }
        }
        return oppdatert
    }

    suspend fun oppdaterStatus(søknadId: UUID, status: BehovsmeldingStatus): Boolean =
        oppdaterStatus(søknadId, Statusendring(status))
}
