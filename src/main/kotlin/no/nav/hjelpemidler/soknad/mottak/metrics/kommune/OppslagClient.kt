package no.nav.hjelpemidler.soknad.mottak.metrics.kommune

import com.fasterxml.jackson.module.kotlin.readValue
import com.github.kittinunf.fuel.core.ResponseDeserializable
import com.github.kittinunf.fuel.coroutines.awaitObjectResponse
import com.github.kittinunf.fuel.httpGet
import no.nav.hjelpemidler.soknad.mottak.Configuration
import no.nav.hjelpemidler.soknad.mottak.JacksonMapper
import org.slf4j.LoggerFactory
import java.util.UUID

class OppslagClient(
    private val oppslagUrl: String = Configuration.oppslagUrl
) {
    suspend fun hentAlleKommuner(): Map<String, KommuneDto> {
        val kommunenrUrl = "$oppslagUrl/geografi/kommunenr"
        LOG.info("Henter alle kommuner fra $kommunenrUrl")

        try {
            return kommunenrUrl.httpGet()
                .header(
                    mapOf(
                        "Accept" to "application/json",
                        "X-Correlation-ID" to UUID.randomUUID().toString()
                    )
                )
                .awaitObjectResponse(
                    object : ResponseDeserializable<Map<String, KommuneDto>> {
                        override fun deserialize(content: String): Map<String, KommuneDto> {
                            return JacksonMapper.objectMapper.readValue(content)
                        }
                    }
                ).third
        } catch (e: Exception) {
            LOG.error("Henting av kommune feilet", e)
            throw e
        }
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(OppslagClient::class.java)
    }
}
