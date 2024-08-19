package no.nav.hjelpemidler.soknad.mottak.client

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import no.nav.hjelpemidler.behovsmeldingsmodell.BehovsmeldingStatus
import no.nav.hjelpemidler.behovsmeldingsmodell.BehovsmeldingType
import no.nav.hjelpemidler.behovsmeldingsmodell.Behovsmeldingsgrunnlag
import no.nav.hjelpemidler.behovsmeldingsmodell.Statusendring
import no.nav.hjelpemidler.behovsmeldingsmodell.SøknadDto
import no.nav.hjelpemidler.behovsmeldingsmodell.SøknadId
import no.nav.hjelpemidler.behovsmeldingsmodell.ordre.Ordrelinje
import no.nav.hjelpemidler.behovsmeldingsmodell.sak.Fagsak
import no.nav.hjelpemidler.behovsmeldingsmodell.sak.HotsakSakId
import no.nav.hjelpemidler.behovsmeldingsmodell.sak.Sakstilknytning
import no.nav.hjelpemidler.behovsmeldingsmodell.sak.Vedtaksresultat
import no.nav.hjelpemidler.http.correlationId
import no.nav.hjelpemidler.http.openid.TokenSetProvider
import no.nav.hjelpemidler.http.openid.openID
import no.nav.hjelpemidler.soknad.mottak.httpClient
import java.util.UUID

private val logger = KotlinLogging.logger {}

class SøknadsbehandlingClient(
    private val baseUrl: String,
    private val tokenSetProvider: TokenSetProvider,
) {
    private val httpClient: HttpClient = httpClient {
        openID(tokenSetProvider)
        defaultRequest {
            accept(ContentType.Application.Json)
            contentType(ContentType.Application.Json)
            correlationId()
        }
    }

    suspend fun finnSøknad(søknadId: SøknadId, inkluderData: Boolean = false): SøknadDto? {
        return httpClient
            .get("$baseUrl/soknad/$søknadId") {
                parameter("inkluderData", inkluderData)
            }
            .body<SøknadDto?>()
    }

    suspend fun hentSøknad(søknadId: SøknadId, inkluderData: Boolean = false): SøknadDto {
        return checkNotNull(finnSøknad(søknadId, inkluderData)) {
            "Fant ikke søknad med søknadId: $søknadId"
        }
    }

    suspend fun lagreBehovsmelding(grunnlag: Behovsmeldingsgrunnlag): Int {
        return httpClient
            .post("$baseUrl/soknad") {
                setBody(grunnlag)
            }
            .body<Int>()
    }

    suspend fun oppdaterJournalpostId(søknadId: UUID, journalpostId: String): Int {
        return httpClient
            .put("$baseUrl/soknad/$søknadId/journalpost") {
                setBody(mapOf("journalpostId" to journalpostId))
            }
            .body<Int>()
    }

    suspend fun oppdaterOppgaveId(søknadId: UUID, oppgaveId: String): Int {
        return httpClient
            .put("$baseUrl/soknad/$søknadId/oppgave") {
                setBody(mapOf("oppgaveId" to oppgaveId))
            }
            .body<Int>()
    }

    suspend fun lagreSakstilknytning(søknadId: UUID, sakstilknytning: Sakstilknytning): Int {
        return httpClient
            .post("$baseUrl/soknad/$søknadId/sak") {
                setBody(sakstilknytning)
            }
            .body<Int>()
    }

    suspend fun lagreVedtaksresultat(søknadId: UUID, vedtaksresultat: Vedtaksresultat): Int {
        return httpClient
            .post("$baseUrl/soknad/$søknadId/vedtaksresultat") {
                setBody(vedtaksresultat)
            }
            .body<Int>()
    }

    suspend fun lagreOrdrelinje(ordrelinje: Ordrelinje): Int {
        return httpClient
            .post("$baseUrl/soknad/${ordrelinje.søknadId}/ordre") {
                setBody(ordrelinje)
            }
            .body<Int>()
    }

    suspend fun oppdaterStatus(søknadId: SøknadId, status: BehovsmeldingStatus): Int {
        return oppdaterStatus(søknadId, Statusendring(status, null, null))
    }

    suspend fun oppdaterStatus(
        søknadId: SøknadId,
        statusendring: Statusendring,
    ): Int {
        return httpClient
            .put("$baseUrl/soknad/$søknadId/status") {
                setBody(statusendring)
            }
            .body<Int>()
    }

    /**
     * NB! Fungerer kun for Hotsak.
     */
    suspend fun finnSøknadForSak(sakId: HotsakSakId, inkluderData: Boolean = false): SøknadDto? {
        return httpClient
            .get("$baseUrl/sak/$sakId/soknad") {
                parameter("inkluderData", inkluderData)
            }
            .body<SøknadDto?>()
    }

    suspend fun finnSakForSøknad(søknadId: UUID): Fagsak? {
        return httpClient
            .get("$baseUrl/soknad/$søknadId/sak")
            .body<Fagsak?>()
    }

    suspend fun behovsmeldingTypeFor(søknadId: UUID): BehovsmeldingType? {
        return try {
            hentSøknad(søknadId).behovsmeldingstype
        } catch (e: Exception) {
            logger.error(e) { "Feil ved henting av behovsmeldingstype for søknadId: $søknadId" }
            null
        }
    }

    suspend fun hentSøknadIdFraVedtaksresultat(
        fnrBruker: String,
        saksblokkOgSaksnr: String,
    ): List<SøknadIdFraVedtaksresultat> {
        data class Request(
            val fnrBruker: String,
            val saksblokkOgSaksnr: String,
        )
        return httpClient
            .post("$baseUrl/soknad/fra-vedtaksresultat-v2") {
                setBody(Request(fnrBruker, saksblokkOgSaksnr))
            }
            .body<List<SøknadIdFraVedtaksresultat>>()

    }

    suspend fun hentSøknaderTilGodkjenningEldreEnn(dager: Int): List<UtgåttSøknad> {
        return httpClient
            .get("$baseUrl/soknad/utgaatt/$dager")
            .body<List<UtgåttSøknad>>()
    }

    suspend fun ordreSisteDøgn(søknadId: UUID): HarOrdre {
        return httpClient
            .get("$baseUrl/soknad/ordre/ordrelinje-siste-doegn/$søknadId")
            .body<HarOrdre>()
    }

    suspend fun harOrdreForSøknad(søknadId: UUID): HarOrdre {
        return httpClient
            .get("$baseUrl/soknad/ordre/har-ordre/$søknadId")
            .body<HarOrdre>()
    }
}
