package no.nav.hjelpemidler.soknad.mottak.client.hmdb

import com.expediagroup.graphql.client.jackson.GraphQLClientJacksonSerializer
import com.expediagroup.graphql.client.ktor.GraphQLKtorClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.Apache
import mu.KotlinLogging
import no.nav.hjelpemidler.soknad.mottak.Configuration
import no.nav.hjelpemidler.soknad.mottak.client.hmdb.hentprodukter.Produkt
import java.net.URL

object HjelpemiddeldatabaseClient {
    private val log = KotlinLogging.logger {}
    private val client =
        GraphQLKtorClient(
            url = URL("${Configuration.hmdb.grunndataApi}/graphql"),
            httpClient = HttpClient(engineFactory = Apache),
            serializer = GraphQLClientJacksonSerializer()
        )

    suspend fun hentProdukter(hmsnr: Set<String>): List<Produkt> {
        if (hmsnr.isEmpty()) return emptyList()
        log.debug { "Henter produkter med hmsnr=$hmsnr fra hjelpemiddeldatabasen" }
        val request = HentProdukter(variables = HentProdukter.Variables(hmsnr = hmsnr.toList()))
        return try {
            val response = client.execute(request)
            when {
                response.errors != null -> {
                    log.error { "Feil under henting av data fra hjelpemiddeldatabasen, hmsnr=$hmsnr, errors=${response.errors?.map { it.message }}" }
                    throw Exception("Feil under henting av data fra hjelpemiddeldatabasen, hmsnr=$hmsnr, errors=${response.errors?.map { it.message }}")
                }

                response.data != null -> {
                    val produkter = response.data?.produkter ?: emptyList()
                    log.debug { "Hentet ${produkter.size} isokortnavn for produkter fra hjelpemiddeldatabasen" }
                    produkter
                }
                else -> throw Exception("Unexpected response: $response")
            }
        } catch (e: Exception) {
            log.error(e) { "Feil under henting av data fra hjelpemiddeldatabasen, hmsnr=$hmsnr" }
            throw Exception("Feil under henting av data fra hjelpemiddeldatabasen, hmsnr=$hmsnr")
        }
    }
}
