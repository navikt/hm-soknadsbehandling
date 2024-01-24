package no.nav.hjelpemidler.soknad.mottak.client.`hmdb-ng`

import com.expediagroup.graphql.client.jackson.GraphQLClientJacksonSerializer
import com.expediagroup.graphql.client.ktor.GraphQLKtorClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.Apache
import mu.KotlinLogging
import no.nav.hjelpemidler.soknad.mottak.Configuration
import no.nav.hjelpemidler.soknad.mottak.client.`hmdb-ng`.hentprodukter.Product
import java.net.URL

object HjelpemiddeldatabaseNgClient {
    private val log = KotlinLogging.logger {}
    private val client =
        GraphQLKtorClient(
            url = URL("${Configuration.hmdb.grunndataApiNg}/graphql"),
            httpClient = HttpClient(engineFactory = Apache),
            serializer = GraphQLClientJacksonSerializer()
        )

    suspend fun hentProdukter(hmsnrs: Set<String>): List<Product> {
        if (hmsnrs.isEmpty()) return emptyList()
        log.debug { "Henter produkter med hmsnrs=$hmsnrs fra hjelpemiddeldatabasen" }
        val request = HentProdukter(variables = HentProdukter.Variables(hmsnrs = hmsnrs.toList()))
        return try {
            val response = client.execute(request)
            when {
                response.errors != null -> {
                    log.error { "Feil under henting av data fra hjelpemiddeldatabasen, hmsnrs=$hmsnrs, errors=${response.errors?.map { it.message }}" }
                    throw Exception("Feil under henting av data fra hjelpemiddeldatabasen, hmsnrs=$hmsnrs, errors=${response.errors?.map { it.message }}")
                }

                response.data != null -> {
                    val produkter = response.data?.products ?: emptyList()
                    log.debug { "Hentet ${produkter.size} isokortnavn for produkter fra hjelpemiddeldatabasen" }
                    produkter
                }
                else -> throw Exception("Unexpected response: $response")
            }
        } catch (e: Exception) {
            log.error(e) { "Feil under henting av data fra hjelpemiddeldatabasen, hmsnrs=$hmsnrs" }
            throw Exception("Feil under henting av data fra hjelpemiddeldatabasen, hmsnrs=$hmsnrs")
        }
    }
}
