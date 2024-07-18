package no.nav.hjelpemidler.soknad.mottak.client

import com.expediagroup.graphql.client.jackson.GraphQLClientJacksonSerializer
import com.expediagroup.graphql.client.ktor.GraphQLKtorClient
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.Apache
import no.nav.hjelpemidler.soknad.mottak.Configuration
import no.nav.hjelpemidler.soknad.mottak.client.hmdb.HentProdukter
import no.nav.hjelpemidler.soknad.mottak.client.hmdb.hentprodukter.Product
import java.net.URI

object GrunndataClient {
    private val log = KotlinLogging.logger {}
    private val client = GraphQLKtorClient(
        url = URI(Configuration.GRUNNDATA_GRAPHQL_URL).toURL(),
        httpClient = HttpClient(engineFactory = Apache),
        serializer = GraphQLClientJacksonSerializer()
    )

    suspend fun hentProdukter(hmsnrs: Set<String>): List<Product> {
        if (hmsnrs.isEmpty()) return emptyList()
        log.debug { "Henter produkter med hmsnrs: $hmsnrs fra hjelpemiddeldatabasen" }
        val request = HentProdukter(variables = HentProdukter.Variables(hmsnrs = hmsnrs.toList()))
        return try {
            val response = client.execute(request)
            when {
                response.errors != null -> {
                    log.error { "Feil under henting av data fra hjelpemiddeldatabasen, hmsnrs: $hmsnrs, errors: ${response.errors?.map { it.message }}" }
                    throw Exception("Feil under henting av data fra hjelpemiddeldatabasen, hmsnrs: $hmsnrs, errors: ${response.errors?.map { it.message }}")
                }

                response.data != null -> {
                    val produkter = response.data?.products ?: emptyList()
                    log.debug { "Hentet ${produkter.size} isokortnavn for produkter fra hjelpemiddeldatabasen" }
                    produkter
                }

                else -> error("Unexpected response: $response")
            }
        } catch (e: Exception) {
            log.error(e) { "Feil under henting av data fra hjelpemiddeldatabasen, hmsnrs: $hmsnrs" }
            error("Feil under henting av data fra hjelpemiddeldatabasen, hmsnrs: $hmsnrs")
        }
    }
}
