package no.nav.hjelpemidler.soknad.mottak.client

import com.fasterxml.jackson.databind.JsonNode
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.accept
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.nav.hjelpemidler.behovsmeldingsmodell.sak.HotsakSakId
import no.nav.hjelpemidler.behovsmeldingsmodell.sak.Sakstilknytning
import no.nav.hjelpemidler.behovsmeldingsmodell.sak.Vedtaksresultat
import no.nav.hjelpemidler.http.correlationId
import no.nav.hjelpemidler.http.openid.TokenSetProvider
import no.nav.hjelpemidler.http.openid.openID
import no.nav.hjelpemidler.soknad.mottak.httpClient
import no.nav.hjelpemidler.soknad.mottak.river.StatusMedÅrsak
import no.nav.hjelpemidler.soknad.mottak.service.BehovsmeldingType
import no.nav.hjelpemidler.soknad.mottak.service.HarOrdre
import no.nav.hjelpemidler.soknad.mottak.service.OrdrelinjeData
import no.nav.hjelpemidler.soknad.mottak.service.PapirSøknadData
import no.nav.hjelpemidler.soknad.mottak.service.SoknadDataDto
import no.nav.hjelpemidler.soknad.mottak.service.SoknadMedStatus
import no.nav.hjelpemidler.soknad.mottak.service.Status
import no.nav.hjelpemidler.soknad.mottak.service.SøknadData
import no.nav.hjelpemidler.soknad.mottak.service.SøknadIdFraVedtaksresultat
import no.nav.hjelpemidler.soknad.mottak.service.UtgåttSøknad
import java.util.Date
import java.util.UUID

private val logger = KotlinLogging.logger {}

class SøknadForRiverClient(
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

    suspend fun hentSøknad(søknadId: UUID): Søknad {
        return httpClient
            .get("$baseUrl/soknad/$søknadId")
            .body<Søknad>()
    }

    suspend fun lagrePapirsøknad(søknadData: PapirSøknadData): Int {
        return withContext(Dispatchers.IO) {
            runCatching {
                httpClient.post("$baseUrl/soknad/papir") {
                    setBody(søknadData)
                }.body<Int>()
            }.getOrLogAndThrow()
        }
    }

    suspend fun lagreSøknad(søknadData: SøknadData) {
        return withContext(Dispatchers.IO) {
            runCatching {
                httpClient.post("$baseUrl/soknad/bruker") {
                    setBody(søknadData)
                }.body<String>()
            }.getOrLogAndThrow()
        }
    }

    suspend fun lagreSøknad(ordrelinje: OrdrelinjeData): Int {
        return withContext(Dispatchers.IO) {
            runCatching {
                httpClient.post("$baseUrl/ordre") {
                    setBody(ordrelinje)
                }.body<Int>()
            }.getOrLogAndThrow()
        }
    }

    suspend fun søknadFinnes(søknadId: UUID): Boolean {
        return withContext(Dispatchers.IO) {
            runCatching {
                httpClient.get("$baseUrl/soknad/bruker/finnes/$søknadId").body<JsonNode>().get("second").booleanValue()
            }.getOrLogAndThrow()
        }
    }

    suspend fun ordreSisteDøgn(søknadId: UUID): HarOrdre {
        return withContext(Dispatchers.IO) {
            runCatching {
                httpClient.get("$baseUrl/soknad/ordre/ordrelinje-siste-doegn/$søknadId").body<HarOrdre>()
            }.getOrLogAndThrow()
        }
    }

    suspend fun harOrdreForSøknad(søknadId: UUID): HarOrdre {
        return withContext(Dispatchers.IO) {
            runCatching {
                httpClient.get("$baseUrl/soknad/ordre/har-ordre/$søknadId").body<HarOrdre>()
            }.getOrLogAndThrow()
        }
    }

    suspend fun slettSøknad(søknadId: UUID): Int {
        return withContext(Dispatchers.IO) {
            runCatching {
                httpClient.delete("$baseUrl/soknad/bruker") {
                    setBody(søknadId)
                }.body<Int>()
            }.getOrLogAndThrow()
        }
    }

    suspend fun slettUtløptSøknad(søknadId: UUID): Int {
        return withContext(Dispatchers.IO) {
            runCatching {
                httpClient.delete("$baseUrl/soknad/utlopt/bruker") {
                    setBody(søknadId)
                }.body<Int>()
            }.getOrLogAndThrow()
        }
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

    suspend fun hentSøknadIdFraVedtaksresultat(
        fnrBruker: String,
        saksblokkOgSaksnr: String,
    ): List<SøknadIdFraVedtaksresultat> {
        return withContext(Dispatchers.IO) {
            runCatching {
                httpClient.post("$baseUrl/soknad/fra-vedtaksresultat-v2") {
                    setBody(SøknadFraVedtaksresultatDto(fnrBruker, saksblokkOgSaksnr))
                }.body<Array<SøknadIdFraVedtaksresultat>>().toList()
            }.getOrLogAndThrow()
        }
    }

    /**
     * NB! Fungerer kun for Hotsak.
     */
    suspend fun hentSøknadForSak(sakId: HotsakSakId): Søknad {
        return httpClient
            .get("$baseUrl/sak/$sakId/soknad")
            .body<Søknad>()
    }

    suspend fun hentSakForSøknad(søknadId: UUID): Søknad {
        return httpClient
            .get("$baseUrl/soknad/$søknadId/sak")
            .body<Søknad>()
    }

    data class SøknadFraVedtaksresultatDto(
        val fnrBruker: String,
        val saksblokkOgSaksnr: String,
    )

    suspend fun lagreVedtaksresultat(søknadId: UUID, vedtaksresultat: Vedtaksresultat): Int {
        return httpClient
            .post("$baseUrl/soknad/$søknadId/vedtaksresultat") {
                setBody(vedtaksresultat)
            }
            .body<Int>()
    }

    suspend fun fnrOgJournalpostIdFinnes(fnrBruker: String, journalpostId: Int): Boolean {
        return withContext(Dispatchers.IO) {
            runCatching {
                // NB! Skrivefeil i URL
                httpClient.post("$baseUrl/infotrygd/fnr-jounralpost") {
                    setBody(FnrOgJournalpostIdFinnesDto(fnrBruker, journalpostId))
                }.body<JsonNode>()["second"].booleanValue()
            }.getOrLogAndThrow()
        }
    }

    data class FnrOgJournalpostIdFinnesDto(
        val fnrBruker: String,
        val journalpostId: Int,
    )

    suspend fun oppdaterStatus(søknadId: UUID, status: Status): Int {
        return withContext(Dispatchers.IO) {
            runCatching {
                httpClient.put("$baseUrl/soknad/status/$søknadId") { setBody(status) }.body<Int>()
            }.getOrLogAndThrow()
        }
    }

    suspend fun oppdaterStatus(
        statusMedÅrsak: StatusMedÅrsak,
    ): Int {
        return withContext(Dispatchers.IO) {
            runCatching {
                httpClient.put("$baseUrl/soknad/statusV2") { setBody(statusMedÅrsak) }.body<Int>()
            }.getOrLogAndThrow()
        }
    }

    suspend fun hentSøknadData(søknadId: UUID): SøknadData {
        return withContext(Dispatchers.IO) {
            runCatching {
                httpClient.get("$baseUrl/soknadsdata/bruker/$søknadId")
                    .body<SoknadDataDto>()
                    .let { SøknadData.mapFraDto(it) }
            }.getOrLogAndThrow()
        }
    }

    suspend fun hentSøknaderTilGodkjenningEldreEnn(dager: Int): List<UtgåttSøknad> {
        return withContext(Dispatchers.IO) {
            SoknadMedStatus(UUID.randomUUID(), Date(), Date(), Status.UTLØPT, true, "")

            runCatching {
                httpClient.get("$baseUrl/soknad/utgaatt/$dager").body<List<UtgåttSøknad>>()
            }.getOrLogAndThrow()
        }
    }

    suspend fun behovsmeldingTypeFor(søknadId: UUID): BehovsmeldingType? {
        return try {
            hentSøknad(søknadId).behovsmeldingstype
        } catch (e: Exception) {
            logger.error(e) { "Feil ved henting av behovsmeldingstype for søknadId: $søknadId" }
            null
        }
    }
}

private fun <T> Result<T>.getOrLogAndThrow(message: String? = null): T =
    onFailure { logger.error(it) { message ?: it.message } }.getOrThrow()
