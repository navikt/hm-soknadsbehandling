package no.nav.hjelpemidler.soknad.mottak.client

import com.fasterxml.jackson.databind.JsonNode
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.header
import io.ktor.client.request.headers
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpMessageBuilder
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import no.nav.hjelpemidler.soknad.mottak.aad.AzureClient
import no.nav.hjelpemidler.soknad.mottak.httpClient
import no.nav.hjelpemidler.soknad.mottak.river.StatusMedÅrsak
import no.nav.hjelpemidler.soknad.mottak.service.BehovsmeldingType
import no.nav.hjelpemidler.soknad.mottak.service.HarOrdre
import no.nav.hjelpemidler.soknad.mottak.service.OrdrelinjeData
import no.nav.hjelpemidler.soknad.mottak.service.PapirSøknadData
import no.nav.hjelpemidler.soknad.mottak.service.SoknadData
import no.nav.hjelpemidler.soknad.mottak.service.SoknadDataDto
import no.nav.hjelpemidler.soknad.mottak.service.SoknadMedStatus
import no.nav.hjelpemidler.soknad.mottak.service.Status
import no.nav.hjelpemidler.soknad.mottak.service.SøknadIdFraVedtaksresultat
import no.nav.hjelpemidler.soknad.mottak.service.UtgåttSøknad
import no.nav.hjelpemidler.soknad.mottak.service.VedtaksresultatData
import java.time.LocalDate
import java.util.Date
import java.util.UUID

private val logger = KotlinLogging.logger {}

internal interface SøknadForRiverClient {

    suspend fun save(soknadData: SoknadData)
    suspend fun soknadFinnes(soknadsId: UUID): Boolean
    suspend fun ordreSisteDøgn(soknadsId: UUID): HarOrdre
    suspend fun harOrdreForSøknad(soknadsId: UUID): HarOrdre
    suspend fun hentFnrForSoknad(soknadsId: UUID): String
    suspend fun slettSøknad(soknadsId: UUID): Int
    suspend fun hentSøknadsTypeForSøknad(soknadsId: UUID): String?
    suspend fun oppdaterStatus(soknadsId: UUID, status: Status): Int
    suspend fun oppdaterStatus(statusMedÅrsak: StatusMedÅrsak): Int
    suspend fun hentSoknadData(soknadsId: UUID): SoknadData?
    suspend fun hentSoknadOpprettetDato(soknadsId: UUID): Date
    suspend fun hentSoknaderTilGodkjenningEldreEnn(dager: Int): List<UtgåttSøknad>
    suspend fun slettUtløptSøknad(soknadsId: UUID): Int
    suspend fun oppdaterJournalpostId(soknadsId: UUID, journalpostId: String): Int
    suspend fun oppdaterOppgaveId(soknadsId: UUID, oppgaveId: String): Int
    suspend fun lagKnytningMellomHotsakOgSøknad(soknadsId: UUID, sakId: String): Int
    suspend fun lagKnytningMellomFagsakOgSøknad(vedtaksresultatData: VedtaksresultatData): Int
    suspend fun hentSøknadIdFraVedtaksresultat(
        fnrBruker: String,
        saksblokkOgSaksnr: String,
        vedtaksdato: LocalDate
    ): UUID?

    suspend fun behovsmeldingTypeFor(soknadsId: UUID): BehovsmeldingType?

    suspend fun hentSøknadIdFraVedtaksresultatV2(
        fnrBruker: String,
        saksblokkOgSaksnr: String
    ): List<SøknadIdFraVedtaksresultat>

    suspend fun hentSøknadIdFraHotsakSaksnummer(
        saksnummer: String
    ): UUID?

    suspend fun save(ordrelinje: OrdrelinjeData): Int
    suspend fun lagreVedtaksresultat(
        søknadId: UUID,
        vedtaksresultat: String,
        vedtaksdato: LocalDate,
        soknadsType: String
    ): Int

    suspend fun lagreVedtaksresultatFraHotsak(
        søknadId: UUID,
        vedtaksresultat: String,
        vedtaksdato: LocalDate
    ): Int

    suspend fun fnrOgJournalpostIdFinnes(fnrBruker: String, journalpostId: Int): Boolean
    suspend fun savePapir(soknadData: PapirSøknadData): Int
    suspend fun hentGodkjenteSøknaderUtenOppgaveEldreEnn(dager: Int): List<String>
}

internal class SøknadForRiverClientImpl(
    private val baseUrl: String,
    private val azureClient: AzureClient,
    private val accesstokenScope: String,
    private val httpClient: HttpClient = httpClient()
) : SøknadForRiverClient {

    override suspend fun save(soknadData: SoknadData) {
        return withContext(Dispatchers.IO) {
            kotlin.runCatching {
                httpClient.request("$baseUrl/soknad/bruker") {
                    method = HttpMethod.Post
                    headers()
                    setBody(soknadData)
                }.body<String>()
            }.onFailure {
                logger.error { it.message }
            }.getOrThrow()
        }
    }

    override suspend fun savePapir(soknadData: PapirSøknadData): Int {
        return withContext(Dispatchers.IO) {
            kotlin.runCatching {
                httpClient.request("$baseUrl/soknad/papir") {
                    method = HttpMethod.Post
                    headers()
                    setBody(soknadData)
                }.body<Int>()
            }.onFailure {
                logger.error { it.message }
            }.getOrThrow()
        }
    }

    override suspend fun save(ordrelinje: OrdrelinjeData): Int {
        return withContext(Dispatchers.IO) {
            kotlin.runCatching {
                httpClient.request("$baseUrl/ordre") {
                    method = HttpMethod.Post
                    headers()
                    setBody(ordrelinje)
                }.body<Int>()
            }.onFailure {
                logger.error { it.message }
            }.getOrThrow()
        }
    }

    override suspend fun soknadFinnes(soknadsId: UUID): Boolean {
        return withContext(Dispatchers.IO) {
            kotlin.runCatching {
                httpClient.request("$baseUrl/soknad/bruker/finnes/$soknadsId") {
                    method = HttpMethod.Get
                    headers()
                }.body<JsonNode>().get("second").booleanValue()
            }.onFailure {
                logger.error { it.message }
            }
        }.getOrThrow()
    }

    override suspend fun ordreSisteDøgn(soknadsId: UUID): HarOrdre {
        return withContext(Dispatchers.IO) {
            kotlin.runCatching {
                httpClient.request("$baseUrl/soknad/ordre/ordrelinje-siste-doegn/$soknadsId") {
                    method = HttpMethod.Get
                    headers()
                }
                    .body<HarOrdre>()
            }.onFailure {
                logger.error { it.message }
            }
        }.getOrThrow()
    }

    override suspend fun harOrdreForSøknad(soknadsId: UUID): HarOrdre {
        return withContext(Dispatchers.IO) {
            kotlin.runCatching {
                httpClient.request("$baseUrl/soknad/ordre/har-ordre/$soknadsId") {
                    method = HttpMethod.Get
                    headers()
                }.body<HarOrdre>()
            }.onFailure {
                logger.error { it.message }
            }
        }.getOrThrow()
    }

    override suspend fun hentFnrForSoknad(soknadsId: UUID): String {
        return withContext(Dispatchers.IO) {
            kotlin.runCatching {
                httpClient.request("$baseUrl/soknad/fnr/$soknadsId") {
                    method = HttpMethod.Get
                    headers()
                }.body<String>()
            }.onFailure {
                logger.error { it.message }
            }
        }.getOrThrow()
    }

    override suspend fun slettSøknad(soknadsId: UUID): Int {
        return withContext(Dispatchers.IO) {
            kotlin.runCatching {
                httpClient.request("$baseUrl/soknad/bruker") {
                    method = HttpMethod.Delete
                    headers()
                    setBody(soknadsId)
                }.body<Int>()
            }.onFailure {
                logger.error { it.message }
            }.getOrThrow()
        }
    }

    override suspend fun slettUtløptSøknad(soknadsId: UUID): Int {
        return withContext(Dispatchers.IO) {
            kotlin.runCatching {
                httpClient.request("$baseUrl/soknad/utlopt/bruker") {
                    method = HttpMethod.Delete
                    headers()
                    setBody(soknadsId)
                }.body<Int>()
            }.onFailure {
                logger.error { it.message }
            }.getOrThrow()
        }
    }

    override suspend fun oppdaterJournalpostId(soknadsId: UUID, journalpostId: String): Int {
        return withContext(Dispatchers.IO) {
            kotlin.runCatching {
                httpClient.request("$baseUrl/soknad/journalpost-id/$soknadsId") {
                    method = HttpMethod.Put
                    headers()
                    setBody(mapOf("journalpostId" to journalpostId))
                }.body<Int>()
            }.onFailure {
                logger.error { it.message }
            }.getOrThrow()
        }
    }

    override suspend fun oppdaterOppgaveId(soknadsId: UUID, oppgaveId: String): Int {
        return withContext(Dispatchers.IO) {
            kotlin.runCatching {
                httpClient.request("$baseUrl/soknad/oppgave-id/$soknadsId") {
                    method = HttpMethod.Put
                    headers()
                    setBody(mapOf("oppgaveId" to oppgaveId))
                }.body<Int>()
            }.onFailure {
                logger.error { it.message }
            }.getOrThrow()
        }
    }

    override suspend fun lagKnytningMellomFagsakOgSøknad(vedtaksresultatData: VedtaksresultatData): Int {
        return withContext(Dispatchers.IO) {
            kotlin.runCatching {
                httpClient.request("$baseUrl/infotrygd/fagsak") {
                    method = HttpMethod.Post
                    headers()
                    setBody(vedtaksresultatData)
                }.body<Int>()
            }.onFailure {
                logger.error { it.message }
            }.getOrThrow()
        }
    }

    override suspend fun lagKnytningMellomHotsakOgSøknad(soknadsId: UUID, sakId: String): Int {
        return withContext(Dispatchers.IO) {
            kotlin.runCatching {
                httpClient.request("$baseUrl/hotsak/sak") {
                    method = HttpMethod.Post
                    headers()
                    setBody(HotsakTilknytningData(soknadsId, sakId))
                }.body<Int>()
            }.onFailure {
                logger.error { it.message }
            }.getOrThrow()
        }
    }

    data class HotsakTilknytningData(
        val søknadId: UUID,
        val saksnr: String
    )

    override suspend fun hentSøknadIdFraVedtaksresultat(
        fnrBruker: String,
        saksblokkOgSaksnr: String,
        vedtaksdato: LocalDate
    ): UUID? {
        return withContext(Dispatchers.IO) {
            kotlin.runCatching {
                httpClient.request("$baseUrl/soknad/fra-vedtaksresultat") {
                    method = HttpMethod.Post
                    headers()
                    setBody(SoknadFraVedtaksresultatDto(fnrBruker, saksblokkOgSaksnr, vedtaksdato))
                }.body<JsonNode>().let {
                    if (it.get("soknadId").textValue() != null) {
                        UUID.fromString(it.get("soknadId").textValue())
                    } else {
                        null
                    }
                }
            }.onFailure {
                logger.error { it.message }
            }.getOrThrow()
        }
    }

    override suspend fun hentSøknadIdFraVedtaksresultatV2(
        fnrBruker: String,
        saksblokkOgSaksnr: String,
    ): List<SøknadIdFraVedtaksresultat> {
        return withContext(Dispatchers.IO) {
            kotlin.runCatching {
                httpClient.request("$baseUrl/soknad/fra-vedtaksresultat-v2") {
                    method = HttpMethod.Post
                    headers()
                    setBody(SoknadFraVedtaksresultatV2Dto(fnrBruker, saksblokkOgSaksnr))
                }.body<Array<SøknadIdFraVedtaksresultat>>().toList()
            }.onFailure {
                logger.error { it.message }
            }.getOrThrow()
        }
    }

    override suspend fun hentSøknadIdFraHotsakSaksnummer(
        saksnummer: String
    ): UUID? {
        return withContext(Dispatchers.IO) {
            kotlin.runCatching {
                httpClient.request("$baseUrl/soknad/hotsak/fra-saknummer") {
                    method = HttpMethod.Post
                    headers()
                    setBody(SoknadFraHotsakNummerDto(saksnummer))
                }.body<JsonNode>().let {
                    if (it.get("soknadId")?.textValue() != null) {
                        UUID.fromString(it.get("soknadId").textValue())
                    } else {
                        null
                    }
                }
            }.onFailure {
                logger.error { it.message }
            }.getOrThrow()
        }
    }

    data class SoknadFraVedtaksresultatDto(
        val fnrBruker: String,
        val saksblokkOgSaksnr: String,
        val vedtaksdato: LocalDate
    )

    data class SoknadFraVedtaksresultatV2Dto(
        val fnrBruker: String,
        val saksblokkOgSaksnr: String
    )

    data class SoknadFraHotsakNummerDto(val saksnummer: String)

    override suspend fun lagreVedtaksresultat(
        søknadId: UUID,
        vedtaksresultat: String,
        vedtaksdato: LocalDate,
        soknadsType: String
    ): Int {
        return withContext(Dispatchers.IO) {
            kotlin.runCatching {
                httpClient.request("$baseUrl/infotrygd/vedtaksresultat") {
                    method = HttpMethod.Post
                    headers()
                    setBody(VedtaksresultatDto(søknadId, vedtaksresultat, vedtaksdato, soknadsType))
                }.body<Int>()
            }.onFailure {
                logger.error { it.message }
            }.getOrThrow()
        }
    }

    override suspend fun lagreVedtaksresultatFraHotsak(
        søknadId: UUID,
        vedtaksresultat: String,
        vedtaksdato: LocalDate
    ): Int {
        return withContext(Dispatchers.IO) {
            kotlin.runCatching {
                httpClient.request("$baseUrl/hotsak/vedtaksresultat") {
                    method = HttpMethod.Post
                    headers()
                    setBody(VedtaksresultatDto(søknadId, vedtaksresultat, vedtaksdato, "n/a"))
                }.body<Int>()
            }.onFailure {
                logger.error { it.message }
            }.getOrThrow()
        }
    }

    data class VedtaksresultatDto(
        val søknadId: UUID,
        val vedtaksresultat: String,
        val vedtaksdato: LocalDate,
        val soknadsType: String
    )

    override suspend fun fnrOgJournalpostIdFinnes(fnrBruker: String, journalpostId: Int): Boolean {
        return withContext(Dispatchers.IO) {
            kotlin.runCatching {
                httpClient.request("$baseUrl/infotrygd/fnr-jounralpost") {
                    method = HttpMethod.Post
                    headers()
                    setBody(FnrOgJournalpostIdFinnesDto(fnrBruker, journalpostId))
                }.body<JsonNode>()["second"].booleanValue()
            }.onFailure {
                logger.error { it.message }
            }.getOrThrow()
        }
    }

    data class FnrOgJournalpostIdFinnesDto(
        val fnrBruker: String,
        val journalpostId: Int
    )

    override suspend fun hentSøknadsTypeForSøknad(soknadsId: UUID): String? {
        data class Response(val søknadsType: String?)

        return withContext(Dispatchers.IO) {
            kotlin.runCatching {
                httpClient.request("$baseUrl/infotrygd/søknadsType/$soknadsId") {
                    method = HttpMethod.Get
                    headers()
                }.body<Response>().søknadsType
            }.onFailure {
                logger.error { it.message }
            }.getOrThrow()
        }
    }

    override suspend fun oppdaterStatus(soknadsId: UUID, status: Status): Int {
        return withContext(Dispatchers.IO) {
            kotlin.runCatching {
                httpClient.request("$baseUrl/soknad/status/$soknadsId") {
                    method = HttpMethod.Put
                    headers()
                    setBody(status)
                }.body<Int>()
            }.onFailure {
                logger.error { it.message }
            }.getOrThrow()
        }
    }

    override suspend fun oppdaterStatus(
        statusMedÅrsak: StatusMedÅrsak
    ): Int {
        return withContext(Dispatchers.IO) {
            kotlin.runCatching {
                httpClient.request("$baseUrl/soknad/statusV2") {
                    method = HttpMethod.Put
                    headers()
                    setBody(statusMedÅrsak)
                }.body<Int>()
            }.onFailure {
                logger.error { it.message }
            }.getOrThrow()
        }
    }

    override suspend fun hentSoknadData(soknadsId: UUID): SoknadData {
        return withContext(Dispatchers.IO) {
            kotlin.runCatching {
                httpClient.request("$baseUrl/soknadsdata/bruker/$soknadsId") {
                    method = HttpMethod.Get
                    headers()
                }.body<SoknadDataDto>().let { SoknadData.mapFraDto(it) }
            }.onFailure {
                logger.error { it.message }
            }
        }.getOrThrow()
    }

    override suspend fun hentSoknadOpprettetDato(soknadsId: UUID): Date {
        return withContext(Dispatchers.IO) {
            kotlin.runCatching {
                httpClient.request("$baseUrl/soknad/opprettet-dato/$soknadsId") {
                    method = HttpMethod.Get
                    headers()
                }.body<Date>()
            }.onFailure {
                logger.error { it.message }
            }
        }.getOrThrow()
    }

    override suspend fun hentSoknaderTilGodkjenningEldreEnn(dager: Int): List<UtgåttSøknad> {
        return withContext(Dispatchers.IO) {
            SoknadMedStatus(UUID.randomUUID(), Date(), Date(), Status.UTLØPT, true, "")

            kotlin.runCatching {
                httpClient.request("$baseUrl/soknad/utgaatt/$dager") {
                    method = HttpMethod.Get
                    headers()
                }.body<List<UtgåttSøknad>>()
            }.onFailure {
                logger.error { it.message }
            }
        }.getOrThrow()
    }

    override suspend fun hentGodkjenteSøknaderUtenOppgaveEldreEnn(dager: Int): List<String> {
        return withContext(Dispatchers.IO) {
            kotlin.runCatching {
                httpClient.request("$baseUrl/soknad/godkjentUtenOppgave/$dager") {
                    method = HttpMethod.Get
                    headers()
                }.body<List<String>>()
            }.onFailure {
                logger.error(it) { "Feil ved GET $baseUrl/soknad/godkjentUtenOppgave/$dager." }
            }
        }.getOrThrow()
    }

    override suspend fun behovsmeldingTypeFor(soknadsId: UUID): BehovsmeldingType? {
        data class Response(val behovsmeldingType: BehovsmeldingType?)

        val resp = withContext(Dispatchers.IO) {
            kotlin.runCatching {
                httpClient.request("$baseUrl/soknad/behovsmeldingType/$soknadsId") {
                    method = HttpMethod.Get
                    headers()
                }.body<Response>()
            }.onSuccess {
                logger.info("DEBUG DEBUG: Response: $it")
            }.onFailure {
                logger.error(it) { "Feil ved GET $baseUrl/soknad/behovsmeldingType/$soknadsId." }
            }
        }.getOrNull()

        return resp?.behovsmeldingType
    }

    private fun HttpMessageBuilder.headers() = this.headers {
        contentType(ContentType.Application.Json)
        accept(ContentType.Application.Json)
        header("Authorization", "Bearer ${azureClient.getToken(accesstokenScope).accessToken}")
        header("X-Correlation-ID", UUID.randomUUID().toString())
    }
}
