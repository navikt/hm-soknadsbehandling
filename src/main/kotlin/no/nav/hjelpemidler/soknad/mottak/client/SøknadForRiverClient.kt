package no.nav.hjelpemidler.soknad.mottak.client

import com.fasterxml.jackson.databind.JsonNode
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
import mu.KotlinLogging
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
import no.nav.hjelpemidler.soknad.mottak.service.VedtaksresultatData
import java.time.LocalDate
import java.util.Date
import java.util.UUID

private val logger = KotlinLogging.logger {}

interface SøknadForRiverClient {
    suspend fun lagreSøknad(søknadData: SøknadData)
    suspend fun søknadFinnes(søknadId: UUID): Boolean
    suspend fun ordreSisteDøgn(søknadId: UUID): HarOrdre
    suspend fun harOrdreForSøknad(søknadId: UUID): HarOrdre
    suspend fun hentFnrForSøknad(søknadId: UUID): String
    suspend fun slettSøknad(søknadId: UUID): Int
    suspend fun hentSøknadstypeForSøknad(søknadId: UUID): String?
    suspend fun oppdaterStatus(søknadId: UUID, status: Status): Int
    suspend fun oppdaterStatus(statusMedÅrsak: StatusMedÅrsak): Int
    suspend fun hentSøknadData(søknadId: UUID): SøknadData?
    suspend fun hentSøknadOpprettetDato(søknadId: UUID): Date
    suspend fun hentSøknaderTilGodkjenningEldreEnn(dager: Int): List<UtgåttSøknad>
    suspend fun slettUtløptSøknad(søknadId: UUID): Int
    suspend fun oppdaterJournalpostId(søknadId: UUID, journalpostId: String): Int
    suspend fun oppdaterOppgaveId(søknadId: UUID, oppgaveId: String): Int
    suspend fun lagKnytningMellomHotsakOgSøknad(søknadId: UUID, sakId: String): Int
    suspend fun lagKnytningMellomFagsakOgSøknad(vedtaksresultatData: VedtaksresultatData): Int
    suspend fun hentSøknadIdFraVedtaksresultat(
        fnrBruker: String,
        saksblokkOgSaksnr: String,
        vedtaksdato: LocalDate,
    ): UUID?

    suspend fun behovsmeldingTypeFor(søknadId: UUID): BehovsmeldingType?

    suspend fun hentSøknadIdFraVedtaksresultatV2(
        fnrBruker: String,
        saksblokkOgSaksnr: String,
    ): List<SøknadIdFraVedtaksresultat>

    suspend fun hentSøknadIdFraHotsakSaksnummer(
        saksnummer: String,
    ): UUID?

    suspend fun lagreSøknad(ordrelinje: OrdrelinjeData): Int
    suspend fun lagreVedtaksresultat(
        søknadId: UUID,
        vedtaksresultat: String,
        vedtaksdato: LocalDate,
        soknadsType: String,
    ): Int

    suspend fun lagreVedtaksresultatFraHotsak(
        søknadId: UUID,
        vedtaksresultat: String,
        vedtaksdato: LocalDate,
    ): Int

    suspend fun fnrOgJournalpostIdFinnes(fnrBruker: String, journalpostId: Int): Boolean
    suspend fun savePapir(soknadData: PapirSøknadData): Int
}

class SøknadForRiverClientImpl(
    private val baseUrl: String,
    private val tokenSetProvider: TokenSetProvider,
) : SøknadForRiverClient {
    private val httpClient: HttpClient = httpClient {
        openID(tokenSetProvider)
        defaultRequest {
            accept(ContentType.Application.Json)
            contentType(ContentType.Application.Json)
            correlationId()
        }
    }

    override suspend fun lagreSøknad(søknadData: SøknadData) {
        return withContext(Dispatchers.IO) {
            runCatching {
                httpClient.post("$baseUrl/soknad/bruker") {
                    setBody(søknadData)
                }.body<String>()
            }.getOrLogAndThrow()
        }
    }

    override suspend fun savePapir(søknadData: PapirSøknadData): Int {
        return withContext(Dispatchers.IO) {
            runCatching {
                httpClient.post("$baseUrl/soknad/papir") {
                    setBody(søknadData)
                }.body<Int>()
            }.getOrLogAndThrow()
        }
    }

    override suspend fun lagreSøknad(ordrelinje: OrdrelinjeData): Int {
        return withContext(Dispatchers.IO) {
            runCatching {
                httpClient.post("$baseUrl/ordre") {
                    setBody(ordrelinje)
                }.body<Int>()
            }.getOrLogAndThrow()
        }
    }

    override suspend fun søknadFinnes(søknadId: UUID): Boolean {
        return withContext(Dispatchers.IO) {
            runCatching {
                httpClient.get("$baseUrl/soknad/bruker/finnes/$søknadId").body<JsonNode>().get("second").booleanValue()
            }.getOrLogAndThrow()
        }
    }

    override suspend fun ordreSisteDøgn(søknadId: UUID): HarOrdre {
        return withContext(Dispatchers.IO) {
            runCatching {
                httpClient.get("$baseUrl/soknad/ordre/ordrelinje-siste-doegn/$søknadId").body<HarOrdre>()
            }.getOrLogAndThrow()
        }
    }

    override suspend fun harOrdreForSøknad(søknadId: UUID): HarOrdre {
        return withContext(Dispatchers.IO) {
            runCatching {
                httpClient.get("$baseUrl/soknad/ordre/har-ordre/$søknadId").body<HarOrdre>()
            }.getOrLogAndThrow()
        }
    }

    override suspend fun hentFnrForSøknad(søknadId: UUID): String {
        return withContext(Dispatchers.IO) {
            runCatching {
                httpClient.get("$baseUrl/soknad/fnr/$søknadId").body<String>()
            }.getOrLogAndThrow()
        }
    }

    override suspend fun slettSøknad(søknadId: UUID): Int {
        return withContext(Dispatchers.IO) {
            runCatching {
                httpClient.delete("$baseUrl/soknad/bruker") {
                    setBody(søknadId)
                }.body<Int>()
            }.getOrLogAndThrow()
        }
    }

    override suspend fun slettUtløptSøknad(søknadId: UUID): Int {
        return withContext(Dispatchers.IO) {
            runCatching {
                httpClient.delete("$baseUrl/soknad/utlopt/bruker") {
                    setBody(søknadId)
                }.body<Int>()
            }.getOrLogAndThrow()
        }
    }

    override suspend fun oppdaterJournalpostId(søknadId: UUID, journalpostId: String): Int {
        return withContext(Dispatchers.IO) {
            runCatching {
                httpClient.put("$baseUrl/soknad/journalpost-id/$søknadId") {
                    setBody(mapOf("journalpostId" to journalpostId))
                }.body<Int>()
            }.getOrLogAndThrow()
        }
    }

    override suspend fun oppdaterOppgaveId(søknadId: UUID, oppgaveId: String): Int {
        return withContext(Dispatchers.IO) {
            runCatching {
                httpClient.put("$baseUrl/soknad/oppgave-id/$søknadId") {
                    setBody(mapOf("oppgaveId" to oppgaveId))
                }.body<Int>()
            }.getOrLogAndThrow()
        }
    }

    override suspend fun lagKnytningMellomFagsakOgSøknad(vedtaksresultatData: VedtaksresultatData): Int {
        return withContext(Dispatchers.IO) {
            runCatching {
                httpClient.post("$baseUrl/infotrygd/fagsak") {
                    setBody(vedtaksresultatData)
                }.body<Int>()
            }.getOrLogAndThrow()
        }
    }

    override suspend fun lagKnytningMellomHotsakOgSøknad(søknadId: UUID, sakId: String): Int {
        return withContext(Dispatchers.IO) {
            runCatching {
                httpClient.post("$baseUrl/hotsak/sak") {
                    setBody(HotsakTilknytningData(søknadId, sakId))
                }.body<Int>()
            }.getOrLogAndThrow()
        }
    }

    data class HotsakTilknytningData(
        val søknadId: UUID,
        val saksnr: String,
    )

    override suspend fun hentSøknadIdFraVedtaksresultat(
        fnrBruker: String,
        saksblokkOgSaksnr: String,
        vedtaksdato: LocalDate,
    ): UUID? {
        return withContext(Dispatchers.IO) {
            runCatching {
                httpClient.post("$baseUrl/soknad/fra-vedtaksresultat") {
                    setBody(SoknadFraVedtaksresultatDto(fnrBruker, saksblokkOgSaksnr, vedtaksdato))
                }.body<JsonNode>().let {
                    if (it.get("soknadId").textValue() != null) {
                        UUID.fromString(it.get("soknadId").textValue())
                    } else {
                        null
                    }
                }
            }.getOrLogAndThrow()
        }
    }

    override suspend fun hentSøknadIdFraVedtaksresultatV2(
        fnrBruker: String,
        saksblokkOgSaksnr: String,
    ): List<SøknadIdFraVedtaksresultat> {
        return withContext(Dispatchers.IO) {
            runCatching {
                httpClient.post("$baseUrl/soknad/fra-vedtaksresultat-v2") {
                    setBody(SoknadFraVedtaksresultatV2Dto(fnrBruker, saksblokkOgSaksnr))
                }.body<Array<SøknadIdFraVedtaksresultat>>().toList()
            }.getOrLogAndThrow()
        }
    }

    override suspend fun hentSøknadIdFraHotsakSaksnummer(
        saksnummer: String,
    ): UUID? {
        return withContext(Dispatchers.IO) {
            runCatching {
                httpClient.post("$baseUrl/soknad/hotsak/fra-saknummer") {
                    setBody(SoknadFraHotsakNummerDto(saksnummer))
                }.body<JsonNode>().let {
                    if (it.get("soknadId")?.textValue() != null) {
                        UUID.fromString(it.get("soknadId").textValue())
                    } else {
                        null
                    }
                }
            }.getOrLogAndThrow()
        }
    }

    data class SoknadFraVedtaksresultatDto(
        val fnrBruker: String,
        val saksblokkOgSaksnr: String,
        val vedtaksdato: LocalDate,
    )

    data class SoknadFraVedtaksresultatV2Dto(
        val fnrBruker: String,
        val saksblokkOgSaksnr: String,
    )

    data class SoknadFraHotsakNummerDto(val saksnummer: String)

    override suspend fun lagreVedtaksresultat(
        søknadId: UUID,
        vedtaksresultat: String,
        vedtaksdato: LocalDate,
        soknadsType: String,
    ): Int {
        return withContext(Dispatchers.IO) {
            runCatching {
                httpClient.post("$baseUrl/infotrygd/vedtaksresultat") {
                    setBody(VedtaksresultatDto(søknadId, vedtaksresultat, vedtaksdato, soknadsType))
                }.body<Int>()
            }.getOrLogAndThrow()
        }
    }

    override suspend fun lagreVedtaksresultatFraHotsak(
        søknadId: UUID,
        vedtaksresultat: String,
        vedtaksdato: LocalDate,
    ): Int {
        return withContext(Dispatchers.IO) {
            runCatching {
                httpClient.post("$baseUrl/hotsak/vedtaksresultat") {
                    setBody(VedtaksresultatDto(søknadId, vedtaksresultat, vedtaksdato, "n/a"))
                }.body<Int>()
            }.getOrLogAndThrow()
        }
    }

    data class VedtaksresultatDto(
        val søknadId: UUID,
        val vedtaksresultat: String,
        val vedtaksdato: LocalDate,
        val soknadsType: String,
    )

    override suspend fun fnrOgJournalpostIdFinnes(fnrBruker: String, journalpostId: Int): Boolean {
        return withContext(Dispatchers.IO) {
            runCatching {
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

    override suspend fun hentSøknadstypeForSøknad(søknadId: UUID): String? {
        data class Response(val søknadsType: String?)

        return withContext(Dispatchers.IO) {
            runCatching {
                httpClient.get("$baseUrl/infotrygd/søknadsType/$søknadId").body<Response>().søknadsType
            }.getOrLogAndThrow()
        }
    }

    override suspend fun oppdaterStatus(søknadId: UUID, status: Status): Int {
        return withContext(Dispatchers.IO) {
            runCatching {
                httpClient.put("$baseUrl/soknad/status/$søknadId") { setBody(status) }.body<Int>()
            }.getOrLogAndThrow()
        }
    }

    override suspend fun oppdaterStatus(
        statusMedÅrsak: StatusMedÅrsak,
    ): Int {
        return withContext(Dispatchers.IO) {
            runCatching {
                httpClient.put("$baseUrl/soknad/statusV2") { setBody(statusMedÅrsak) }.body<Int>()
            }.getOrLogAndThrow()
        }
    }

    override suspend fun hentSøknadData(søknadId: UUID): SøknadData {
        return withContext(Dispatchers.IO) {
            runCatching {
                httpClient.get("$baseUrl/soknadsdata/bruker/$søknadId")
                    .body<SoknadDataDto>()
                    .let { SøknadData.mapFraDto(it) }
            }.getOrLogAndThrow()
        }
    }

    override suspend fun hentSøknadOpprettetDato(søknadId: UUID): Date {
        return withContext(Dispatchers.IO) {
            runCatching {
                httpClient.get("$baseUrl/soknad/opprettet-dato/$søknadId").body<Date>()
            }.getOrLogAndThrow()
        }
    }

    override suspend fun hentSøknaderTilGodkjenningEldreEnn(dager: Int): List<UtgåttSøknad> {
        return withContext(Dispatchers.IO) {
            SoknadMedStatus(UUID.randomUUID(), Date(), Date(), Status.UTLØPT, true, "")

            runCatching {
                httpClient.get("$baseUrl/soknad/utgaatt/$dager").body<List<UtgåttSøknad>>()
            }.getOrLogAndThrow()
        }
    }

    override suspend fun behovsmeldingTypeFor(søknadId: UUID): BehovsmeldingType? {
        data class Response(val behovsmeldingType: BehovsmeldingType?)

        val response = withContext(Dispatchers.IO) {
            runCatching {
                httpClient.get("$baseUrl/soknad/behovsmeldingType/$søknadId").body<Response>()
            }.onSuccess {
                logger.info("DEBUG DEBUG: Response: $it")
            }.onFailure {
                logger.error(it) { "Feil ved GET $baseUrl/soknad/behovsmeldingType/$søknadId." }
            }
        }.getOrNull()

        return response?.behovsmeldingType
    }
}

private fun <T> Result<T>.getOrLogAndThrow(message: String? = null): T =
    onFailure { logger.error(it) { message ?: it.message } }.getOrThrow()
