package no.nav.hjelpemidler.soknad.mottak.soknadsbehandling

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.hjelpemidler.behovsmeldingsmodell.BehovsmeldingId
import no.nav.hjelpemidler.behovsmeldingsmodell.BehovsmeldingStatus
import no.nav.hjelpemidler.behovsmeldingsmodell.BehovsmeldingType
import no.nav.hjelpemidler.behovsmeldingsmodell.Behovsmeldingsgrunnlag
import no.nav.hjelpemidler.behovsmeldingsmodell.Statusendring
import no.nav.hjelpemidler.behovsmeldingsmodell.SøknadDto
import no.nav.hjelpemidler.behovsmeldingsmodell.ordre.Ordrelinje
import no.nav.hjelpemidler.behovsmeldingsmodell.sak.HotsakSakId
import no.nav.hjelpemidler.behovsmeldingsmodell.sak.Sakstilknytning
import no.nav.hjelpemidler.behovsmeldingsmodell.sak.Vedtaksresultat
import no.nav.hjelpemidler.soknad.mottak.client.HarOrdre
import no.nav.hjelpemidler.soknad.mottak.client.SøknadIdFraVedtaksresultat
import no.nav.hjelpemidler.soknad.mottak.client.SøknadsbehandlingClient
import no.nav.hjelpemidler.soknad.mottak.metrics.Prometheus
import java.util.UUID

private val log = KotlinLogging.logger {}

class SøknadsbehandlingService(private val søknadsbehandlingClient: SøknadsbehandlingClient) {
    suspend fun lagreBehovsmelding(grunnlag: Behovsmeldingsgrunnlag): Boolean {
        val søknadId = grunnlag.søknadId
        log.info { "Lagrer behovsmelding, søknadId: $søknadId, kilde: ${grunnlag.kilde}" }
        val lagret = søknadsbehandlingClient.lagreBehovsmelding(grunnlag) > 0
        if (lagret) {
            log.info { "Behovsmelding ble lagret, søknadId: $søknadId" }
        } else {
            log.warn { "Behovsmelding ble ikke lagret, søknadId: $søknadId" }
        }
        return lagret
    }

    suspend fun finnSøknad(søknadId: BehovsmeldingId): SøknadDto? {
        log.info { "Finner søknad, søknadId: $søknadId" }
        return søknadsbehandlingClient.finnSøknad(søknadId)
    }

    suspend fun hentSøknad(søknadId: BehovsmeldingId): SøknadDto {
        log.info { "Henter søknad, søknadId: $søknadId" }
        return søknadsbehandlingClient.hentSøknad(søknadId)
    }

    suspend fun hentBehovsmeldingstype(søknadId: BehovsmeldingId): BehovsmeldingType {
        return try {
            hentSøknad(søknadId).behovsmeldingstype
        } catch (e: Exception) {
            log.error(e) { "Feil ved henting av behovsmeldingstype for søknadId: $søknadId" }
            BehovsmeldingType.SØKNAD
        }
    }

    suspend fun oppdaterStatus(søknadId: BehovsmeldingId, statusendring: Statusendring): Boolean {
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
        oppdaterStatus(søknadId, Statusendring(status, null, null))

    suspend fun lagreSakstilknytning(søknadId: UUID, sakstilknytning: Sakstilknytning): Boolean {
        log.info { "Lagrer sakstilknytning for søknad, søknadId: $søknadId, sakId: ${sakstilknytning.sakId}, system: ${sakstilknytning.system}" }
        val lagret = søknadsbehandlingClient.lagreSakstilknytning(søknadId, sakstilknytning) > 0
        if (lagret) {
            log.info { "Sakstilknytning ble lagret, søknadId: $søknadId, sakId: ${sakstilknytning.sakId}" }
            when (sakstilknytning) {
                is Sakstilknytning.Infotrygd -> Prometheus.sakstilknytningInfotrygdLagretCounter.increment()
                is Sakstilknytning.Hotsak -> Prometheus.sakstilknytningHotsakLagretCounter.increment()
            }
        } else {
            log.warn { "Sakstilknytning ble ikke lagret, søknadId: $søknadId, sakId: ${sakstilknytning.sakId}" }
        }
        return lagret
    }

    suspend fun lagreVedtaksresultat(søknadId: UUID, vedtaksresultat: Vedtaksresultat): Boolean {
        log.info { "Lagrer vedtaksresultat for søknad, søknadId: $søknadId, system: ${vedtaksresultat.system}" }
        val result = søknadsbehandlingClient.lagreVedtaksresultat(søknadId, vedtaksresultat)
        val lagret = result > 0
        if (lagret) {
            log.info { "Vedtaksresultat ble lagret, søknadId: $søknadId" }
            when (vedtaksresultat) {
                is Vedtaksresultat.Infotrygd -> Prometheus.vedtaksresultatInfotrygdLagretCounter.increment()
                is Vedtaksresultat.Hotsak -> Prometheus.vedtaksresultatHotsakLagretCounter.increment()
            }
        } else {
            log.warn { "Vedtaksresultat ble ikke lagret, søknadId: $søknadId" }
        }
        return lagret
    }

    suspend fun lagreOrdrelinje(ordrelinje: Ordrelinje): Boolean {
        val søknadId = ordrelinje.søknadId
        val serviceforespørsel = ordrelinje.serviceforespørsel
        val ordrenr = ordrelinje.ordrenr
        val ordrelinje1 = ordrelinje.ordrelinje
        val delordrelinje = ordrelinje.delordrelinje
        try {
            val lagret = søknadsbehandlingClient.lagreOrdrelinje(ordrelinje) > 0
            if (lagret) {
                log.info { "Lagret ordrelinje for SF: $serviceforespørsel, ordrenr: $ordrenr og ordrelinje/delordrelinje: $ordrelinje1/$delordrelinje, søknadId: $søknadId" }
            } else {
                log.warn { "Duplikat av ordrelinje for SF: $serviceforespørsel, ordrenr: $ordrenr og ordrelinje/delordrelinje: $ordrelinje1/$delordrelinje ble ikke lagret, søknadId: $søknadId" }
            }
            return lagret
        } catch (e: Exception) {
            log.error(e) { "Feil under lagring av ordrelinje for SF: $serviceforespørsel, ordrenr: $ordrenr og ordrelinje/delordrelinje: $ordrelinje1/$delordrelinje, søknadId: $søknadId" }
            throw e
        }
    }

    suspend fun finnSøknadForSak(sakId: HotsakSakId, inkluderData: Boolean = false): SøknadDto? {
        return søknadsbehandlingClient.finnSøknadForSak(sakId, inkluderData)
    }

    suspend fun hentSøknadIdFraVedtaksresultat(
        fnrBruker: String,
        saksblokkOgSaksnr: String,
    ): List<SøknadIdFraVedtaksresultat> {
        return søknadsbehandlingClient.hentSøknadIdFraVedtaksresultat(fnrBruker, saksblokkOgSaksnr)
    }

    suspend fun harOrdreForSøknad(søknadId: UUID): HarOrdre {
        return søknadsbehandlingClient.harOrdreForSøknad(søknadId)
    }

    suspend fun ordreSisteDøgn(søknadId: UUID): HarOrdre {
        return søknadsbehandlingClient.ordreSisteDøgn(søknadId)
    }

    suspend fun slettSøknad(søknadId: BehovsmeldingId): Boolean {
        log.info { "Sletter søknad, søknadId: $søknadId" }
        val result = søknadsbehandlingClient.oppdaterStatus(søknadId, BehovsmeldingStatus.SLETTET)
        val slettet = result > 0
        if (slettet) {
            log.info { "Søknad ble slettet, søknadId: $søknadId" }
        } else {
            log.warn { "Søknad ble ikke slettet (eller er allerede slettet), søknadId: $søknadId" }
        }
        return slettet
    }
}
