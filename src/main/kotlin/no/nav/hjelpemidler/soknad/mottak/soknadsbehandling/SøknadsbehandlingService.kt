package no.nav.hjelpemidler.soknad.mottak.soknadsbehandling

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.hjelpemidler.behovsmeldingsmodell.BehovsmeldingStatus
import no.nav.hjelpemidler.behovsmeldingsmodell.BehovsmeldingType
import no.nav.hjelpemidler.behovsmeldingsmodell.SøknadId
import no.nav.hjelpemidler.behovsmeldingsmodell.sak.Sakstilknytning
import no.nav.hjelpemidler.behovsmeldingsmodell.sak.Vedtaksresultat
import no.nav.hjelpemidler.soknad.mottak.client.HarOrdre
import no.nav.hjelpemidler.soknad.mottak.client.Søknad
import no.nav.hjelpemidler.soknad.mottak.service.BehovsmeldingGrunnlag
import no.nav.hjelpemidler.soknad.mottak.service.PapirsøknadData
import no.nav.hjelpemidler.soknad.mottak.service.SøknadData
import java.util.UUID
import no.nav.hjelpemidler.soknad.mottak.client.SøknadForRiverClient as SøknadsbehandlingClient

private val log = KotlinLogging.logger {}

class SøknadsbehandlingService(private val søknadsbehandlingClient: SøknadsbehandlingClient) {
    suspend fun lagreBehovsmelding(grunnlag: BehovsmeldingGrunnlag) {
        val søknadId = grunnlag.søknadId
        log.info { "Lagrer behovsmelding, søknadId: $søknadId" }
        val lagret = when (grunnlag) {
            is PapirsøknadData -> søknadsbehandlingClient.lagrePapirsøknad(grunnlag)
            is SøknadData -> søknadsbehandlingClient.lagreDigitalSøknad(grunnlag)
        } > 0
        if (lagret) {
            log.info { "Behovsmelding ble lagret, søknadId: $søknadId" }
        } else {
            log.warn { "Behovsmelding ble ikke lagret, søknadId: $søknadId" }
        }
    }

    suspend fun finnSøknad(søknadId: SøknadId, inkluderData: Boolean = false): Søknad? {
        log.info { "Finner søknad, søknadId: $søknadId, inkluderData: $inkluderData" }
        return søknadsbehandlingClient.finnSøknad(søknadId, inkluderData)
    }

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

    suspend fun lagreSakstilknytning(søknadId: UUID, sakstilknytning: Sakstilknytning): Boolean {
        // todo -> metrics
        log.info { "Lagrer sakstilknytning for søknad, søknadId: $søknadId, sakId: ${sakstilknytning.sakId}, system: ${sakstilknytning.system}" }
        val result = søknadsbehandlingClient.lagreSakstilknytning(søknadId, sakstilknytning)
        val lagret = result > 0
        if (lagret) {
            log.info { "Sakstilknytning ble lagret, søknadId: $søknadId, sakId: ${sakstilknytning.sakId}" }
        } else {
            log.warn { "Sakstilknytning ble ikke lagret, søknadId: $søknadId, sakId: ${sakstilknytning.sakId}" }
        }
        return lagret
    }

    suspend fun lagreVedtaksresultat(søknadId: UUID, vedtaksresultat: Vedtaksresultat): Boolean {
        // todo -> metrics
        log.info { "Lagrer vedtaksresultat for søknad, søknadId: $søknadId, system: ${vedtaksresultat.system}" }
        val result = søknadsbehandlingClient.lagreVedtaksresultat(søknadId, vedtaksresultat)
        val lagret = result > 0
        if (lagret) {
            log.info { "Vedtaksresultat ble lagret, søknadId: $søknadId" }
        } else {
            log.warn { "Vedtaksresultat ble ikke lagret, søknadId: $søknadId" }
        }
        return lagret
    }

    suspend fun harOrdreForSøknad(søknadId: UUID): HarOrdre {
        return søknadsbehandlingClient.harOrdreForSøknad(søknadId)
    }

    suspend fun slettSøknad(søknadId: SøknadId): Boolean {
        log.info { "Sletter søknad, søknadId: $søknadId" }
        val result = søknadsbehandlingClient.slettSøknad(søknadId)
        val slettet = result > 0
        if (slettet) {
            log.info { "Søknad ble slettet, søknadId: $søknadId" }
        } else {
            log.warn { "Søknad ble ikke slettet (eller er allerede slettet), søknadId: $søknadId" }
        }
        return slettet
    }

    suspend fun fnrOgJournalpostIdFinnes(fnrBruker: String, journalpostId: String): Boolean {
        return søknadsbehandlingClient.fnrOgJournalpostIdFinnes(fnrBruker, journalpostId)
    }
}
