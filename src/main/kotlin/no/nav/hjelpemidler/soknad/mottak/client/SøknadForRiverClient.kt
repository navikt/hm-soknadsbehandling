package no.nav.hjelpemidler.soknad.mottak.client

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.github.kittinunf.fuel.core.ResponseDeserializable
import com.github.kittinunf.fuel.core.extensions.jsonBody
import com.github.kittinunf.fuel.coroutines.awaitObject
import com.github.kittinunf.fuel.coroutines.awaitObjectResponse
import com.github.kittinunf.fuel.coroutines.awaitStringResponse
import com.github.kittinunf.fuel.httpDelete
import com.github.kittinunf.fuel.httpGet
import com.github.kittinunf.fuel.httpPost
import com.github.kittinunf.fuel.httpPut
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import no.nav.hjelpemidler.soknad.mottak.aad.AzureClient
import no.nav.hjelpemidler.soknad.mottak.service.OrdrelinjeData
import no.nav.hjelpemidler.soknad.mottak.service.PapirSøknadData
import no.nav.hjelpemidler.soknad.mottak.service.SoknadData
import no.nav.hjelpemidler.soknad.mottak.service.SoknadDataDto
import no.nav.hjelpemidler.soknad.mottak.service.SoknadMedStatus
import no.nav.hjelpemidler.soknad.mottak.service.Status
import no.nav.hjelpemidler.soknad.mottak.service.UtgåttSøknad
import no.nav.hjelpemidler.soknad.mottak.service.VedtaksresultatData
import java.time.LocalDate
import java.util.Date
import java.util.UUID

private val logger = KotlinLogging.logger {}

internal interface SøknadForRiverClient {

    suspend fun save(soknadData: SoknadData)
    suspend fun soknadFinnes(soknadsId: UUID): Boolean
    suspend fun hentFnrForSoknad(soknadsId: UUID): String
    suspend fun slettSøknad(soknadsId: UUID): Int
    suspend fun oppdaterStatus(soknadsId: UUID, status: Status): Int
    suspend fun hentSoknadData(soknadsId: UUID): SoknadData?
    suspend fun hentSoknadOpprettetDato(soknadsId: UUID): Date
    suspend fun hentSoknaderTilGodkjenningEldreEnn(dager: Int): List<UtgåttSøknad>
    suspend fun slettUtløptSøknad(soknadsId: UUID): Int
    suspend fun oppdaterJournalpostId(soknadsId: UUID, journalpostId: String): Int
    suspend fun oppdaterOppgaveId(soknadsId: UUID, oppgaveId: String): Int
    suspend fun lagKnytningMellomFagsakOgSøknad(vedtaksresultatData: VedtaksresultatData): Int
    suspend fun hentSøknadIdFraVedtaksresultat(
        fnrBruker: String,
        saksblokkOgSaksnr: String,
        vedtaksdato: LocalDate
    ): UUID?

    suspend fun save(ordrelinje: OrdrelinjeData): Int
    suspend fun lagreVedtaksresultat(
        søknadId: UUID,
        vedtaksresultat: String,
        vedtaksdato: LocalDate
    ): Int

    suspend fun fnrOgJournalpostIdFinnes(fnrBruker: String, journalpostId: Int): Boolean
    suspend fun savePapir(soknadData: PapirSøknadData): Int
}

internal class SøknadForRiverClientImpl(
    private val baseUrl: String,
    private val azureClient: AzureClient,
    private val accesstokenScope: String,
) : SøknadForRiverClient {

    override suspend fun save(soknadData: SoknadData) {
        return withContext(Dispatchers.IO) {
            kotlin.runCatching {

                "$baseUrl/soknad/bruker".httpPost()
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("Authorization", "Bearer ${azureClient.getToken(accesstokenScope).accessToken}")
                    .header("X-Correlation-ID", UUID.randomUUID().toString())
                    .jsonBody(ObjectMapper().writeValueAsString(soknadData))
                    .awaitStringResponse()
            }
                .onFailure {
                    logger.error { it.message }
                }
        }
    }

    override suspend fun savePapir(papirSøknadData: PapirSøknadData): Int {
        return withContext(Dispatchers.IO) {
            kotlin.runCatching {

                "$baseUrl/soknad/papir".httpPost()
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("Authorization", "Bearer ${azureClient.getToken(accesstokenScope).accessToken}")
                    .header("X-Correlation-ID", UUID.randomUUID().toString())
                    .jsonBody(ObjectMapper().writeValueAsString(papirSøknadData))
                    .awaitStringResponse().third.toInt()
            }
                .onFailure {
                    logger.error { it.message }
                }
                .getOrThrow()
        }
    }

    override suspend fun save(ordrelinje: OrdrelinjeData): Int {
        return withContext(Dispatchers.IO) {
            kotlin.runCatching {

                "$baseUrl/ordre".httpPost()
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("Authorization", "Bearer ${azureClient.getToken(accesstokenScope).accessToken}")
                    .header("X-Correlation-ID", UUID.randomUUID().toString())
                    .jsonBody(ObjectMapper().writeValueAsString(ordrelinje))
                    .awaitStringResponse().third.toInt()
            }
                .onFailure {
                    logger.error { it.message }
                }
                .getOrThrow()
        }
    }

    override suspend fun soknadFinnes(soknadsId: UUID): Boolean {
        return withContext(Dispatchers.IO) {
            kotlin.runCatching {

                "$baseUrl/soknad/bruker/finnes/$soknadsId".httpGet()
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("Authorization", "Bearer ${azureClient.getToken(accesstokenScope).accessToken}")
                    .header("X-Correlation-ID", UUID.randomUUID().toString())
                    .awaitObject(
                        object : ResponseDeserializable<JsonNode> {
                            override fun deserialize(content: String): JsonNode {
                                return ObjectMapper().readTree(content)
                            }
                        }
                    )
                    .let {
                        it.get("second").booleanValue()
                    }
            }
                .onFailure {
                    logger.error { it.message }
                }
        }
            .getOrThrow()
    }

    override suspend fun hentFnrForSoknad(soknadsId: UUID): String {
        return withContext(Dispatchers.IO) {
            kotlin.runCatching {

                "$baseUrl/soknad/fnr/$soknadsId".httpGet()
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("Authorization", "Bearer ${azureClient.getToken(accesstokenScope).accessToken}")
                    .header("X-Correlation-ID", UUID.randomUUID().toString())
                    .awaitStringResponse()
                    .let {
                        it.third
                    }
            }
                .onFailure {
                    logger.error { it.message }
                }
        }
            .getOrThrow()
    }

    override suspend fun slettSøknad(soknadsId: UUID): Int {
        return withContext(Dispatchers.IO) {
            kotlin.runCatching {

                "$baseUrl/soknad/bruker".httpDelete()
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("Authorization", "Bearer ${azureClient.getToken(accesstokenScope).accessToken}")
                    .header("X-Correlation-ID", UUID.randomUUID().toString())
                    .jsonBody(ObjectMapper().writeValueAsString(soknadsId))
                    .awaitStringResponse().third.toInt()
            }
                .onFailure {
                    logger.error { it.message }
                }
                .getOrThrow()
        }
    }

    override suspend fun slettUtløptSøknad(soknadsId: UUID): Int {
        return withContext(Dispatchers.IO) {
            kotlin.runCatching {

                "$baseUrl/soknad/utlopt/bruker".httpDelete()
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("Authorization", "Bearer ${azureClient.getToken(accesstokenScope).accessToken}")
                    .header("X-Correlation-ID", UUID.randomUUID().toString())
                    .jsonBody(ObjectMapper().writeValueAsString(soknadsId))
                    .awaitStringResponse().third.toInt()
            }
                .onFailure {
                    logger.error { it.message }
                }
                .getOrThrow()
        }
    }

    override suspend fun oppdaterJournalpostId(soknadsId: UUID, journalpostId: String): Int {
        return withContext(Dispatchers.IO) {
            kotlin.runCatching {

                "$baseUrl/soknad/journalpost-id/$soknadsId".httpPut()
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("Authorization", "Bearer ${azureClient.getToken(accesstokenScope).accessToken}")
                    .header("X-Correlation-ID", UUID.randomUUID().toString())
                    .jsonBody(ObjectMapper().writeValueAsString(journalpostId))
                    .awaitStringResponse().third.toInt()
            }
                .onFailure {
                    logger.error { it.message }
                }
                .getOrThrow()
        }
    }

    override suspend fun oppdaterOppgaveId(soknadsId: UUID, oppgaveId: String): Int {
        return withContext(Dispatchers.IO) {
            kotlin.runCatching {

                "$baseUrl/soknad/oppgave-id/$soknadsId".httpPut()
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("Authorization", "Bearer ${azureClient.getToken(accesstokenScope).accessToken}")
                    .header("X-Correlation-ID", UUID.randomUUID().toString())
                    .jsonBody(ObjectMapper().writeValueAsString(oppgaveId))
                    .awaitStringResponse().third.toInt()
            }
                .onFailure {
                    logger.error { it.message }
                }
                .getOrThrow()
        }
    }

    override suspend fun lagKnytningMellomFagsakOgSøknad(vedtaksresultatData: VedtaksresultatData): Int {
        return withContext(Dispatchers.IO) {
            kotlin.runCatching {

                "$baseUrl/infotrygd/fagsak".httpPost()
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("Authorization", "Bearer ${azureClient.getToken(accesstokenScope).accessToken}")
                    .header("X-Correlation-ID", UUID.randomUUID().toString())
                    .jsonBody(ObjectMapper().writeValueAsString(vedtaksresultatData))
                    .awaitStringResponse().third.toInt()
            }
                .onFailure {
                    logger.error { it.message }
                }
                .getOrThrow()
        }
    }

    override suspend fun hentSøknadIdFraVedtaksresultat(
        fnrBruker: String,
        saksblokkOgSaksnr: String,
        vedtaksdato: LocalDate
    ): UUID? {
        return withContext(Dispatchers.IO) {
            kotlin.runCatching {

                "$baseUrl/soknad/fra-vedtaksresultat".httpPost()
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("Authorization", "Bearer ${azureClient.getToken(accesstokenScope).accessToken}")
                    .header("X-Correlation-ID", UUID.randomUUID().toString())
                    .jsonBody(
                        ObjectMapper().writeValueAsString(
                            SoknadFraVedtaksresultatDto(
                                fnrBruker,
                                saksblokkOgSaksnr,
                                vedtaksdato
                            )
                        )
                    )
                    .awaitStringResponse()
                    .let {
                        UUID.fromString(it.third)
                    }
            }
                .onFailure {
                    logger.error { it.message }
                }
                .getOrThrow()
        }
    }

    data class SoknadFraVedtaksresultatDto(
        val fnrBruker: String,
        val saksblokkOgSaksnr: String,
        val vedtaksdato: LocalDate
    )

    override suspend fun lagreVedtaksresultat(søknadId: UUID, vedtaksresultat: String, vedtaksdato: LocalDate): Int {
        return withContext(Dispatchers.IO) {
            kotlin.runCatching {

                "$baseUrl/infotrygd/vedtaksresultat".httpPost()
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("Authorization", "Bearer ${azureClient.getToken(accesstokenScope).accessToken}")
                    .header("X-Correlation-ID", UUID.randomUUID().toString())
                    .jsonBody(
                        ObjectMapper().writeValueAsString(
                            VedtaksresultatDto(
                                søknadId,
                                vedtaksresultat,
                                vedtaksdato
                            )
                        )
                    )
                    .awaitStringResponse().third.toInt()
            }
                .onFailure {
                    logger.error { it.message }
                }
                .getOrThrow()
        }
    }

    data class VedtaksresultatDto(
        val søknadId: UUID,
        val vedtaksresultat: String,
        val vedtaksdato: LocalDate
    )

    override suspend fun fnrOgJournalpostIdFinnes(fnrBruker: String, journalpostId: Int): Boolean {
        return withContext(Dispatchers.IO) {
            kotlin.runCatching {

                "$baseUrl/infotrygd/fnr-jounralpost".httpPost()
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("Authorization", "Bearer ${azureClient.getToken(accesstokenScope).accessToken}")
                    .header("X-Correlation-ID", UUID.randomUUID().toString())
                    .jsonBody(
                        ObjectMapper().writeValueAsString(
                            FnrOgJournalpostIdFinnesDto(
                                fnrBruker,
                                journalpostId
                            )
                        )
                    )
                    .awaitObject(
                        object : ResponseDeserializable<JsonNode> {
                            override fun deserialize(content: String): JsonNode {
                                return ObjectMapper().readTree(content)
                            }
                        }
                    )
                    .let {
                        it.get("second").booleanValue()
                    }
            }
                .onFailure {
                    logger.error { it.message }
                }
                .getOrThrow()
        }
    }

    data class FnrOgJournalpostIdFinnesDto(
        val fnrBruker: String,
        val journalpostId: Int
    )

    override suspend fun oppdaterStatus(soknadsId: UUID, status: Status): Int {
        return withContext(Dispatchers.IO) {
            kotlin.runCatching {

                "$baseUrl/soknad/status/$soknadsId".httpPut()
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("Authorization", "Bearer ${azureClient.getToken(accesstokenScope).accessToken}")
                    .header("X-Correlation-ID", UUID.randomUUID().toString())
                    .jsonBody(ObjectMapper().writeValueAsString(status))
                    .awaitStringResponse().third.toInt()
            }
                .onFailure {
                    logger.error { it.message }
                }
                .getOrThrow()
        }
    }

    override suspend fun hentSoknadData(soknadsId: UUID): SoknadData? {
        return withContext(Dispatchers.IO) {

            kotlin.runCatching {

                "$baseUrl/soknadsdata/bruker/$soknadsId".httpGet()
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("Authorization", "Bearer ${azureClient.getToken(accesstokenScope).accessToken}")
                    .header("X-Correlation-ID", UUID.randomUUID().toString())
                    .awaitObjectResponse(
                        object : ResponseDeserializable<SoknadDataDto> {
                            override fun deserialize(content: String): SoknadDataDto {
                                return ObjectMapper().readValue(content, SoknadDataDto::class.java)
                            }
                        }
                    ).third
                    .let { SoknadData.mapFraDto(it) }
            }
                .onFailure {
                    logger.error { it.message }
                }
        }
            .getOrThrow()
    }

    override suspend fun hentSoknadOpprettetDato(soknadsId: UUID): Date {
        return withContext(Dispatchers.IO) {

            kotlin.runCatching {

                "$baseUrl/soknad/opprettet-dato/$soknadsId".httpGet()
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("Authorization", "Bearer ${azureClient.getToken(accesstokenScope).accessToken}")
                    .header("X-Correlation-ID", UUID.randomUUID().toString())
                    .awaitObjectResponse(
                        object : ResponseDeserializable<Date> {
                            override fun deserialize(content: String): Date {
                                return ObjectMapper().readValue(content, Date::class.java)
                            }
                        }
                    ).third
            }
                .onFailure {
                    logger.error { it.message }
                }
        }
            .getOrThrow()
    }

    override suspend fun hentSoknaderTilGodkjenningEldreEnn(dager: Int): List<UtgåttSøknad> {
        return withContext(Dispatchers.IO) {

            SoknadMedStatus(UUID.randomUUID(), Date(), Date(), Status.UTLØPT, true, "")
            kotlin.runCatching {

                "$baseUrl/soknad/utgaatt/$dager".httpGet()
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("Authorization", "Bearer ${azureClient.getToken(accesstokenScope).accessToken}")
                    .header("X-Correlation-ID", UUID.randomUUID().toString())
                    .awaitObjectResponse(
                        object : ResponseDeserializable<List<UtgåttSøknad>> {
                            override fun deserialize(content: String): List<UtgåttSøknad> {
                                return ObjectMapper().readValue(content, Array<UtgåttSøknad>::class.java).toList()
                            }
                        }
                    ).third
            }
                .onFailure {
                    logger.error { it.message }
                }
        }
            .getOrThrow()
    }
}
