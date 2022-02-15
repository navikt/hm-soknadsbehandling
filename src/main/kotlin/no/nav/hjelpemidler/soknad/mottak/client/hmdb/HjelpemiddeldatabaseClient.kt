package no.nav.hjelpemidler.soknad.mottak.client.hmdb

import com.expediagroup.graphql.client.jackson.GraphQLClientJacksonSerializer
import com.expediagroup.graphql.client.ktor.GraphQLKtorClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.Apache
import mu.KotlinLogging
import no.nav.hjelpemidler.soknad.mottak.Configuration
import no.nav.hjelpemidler.soknad.mottak.client.hmdb.hentisokortnavnforproduktermedhmsnrs.Produkt
import java.net.URL

object HjelpemiddeldatabaseClient {
    private val logg = KotlinLogging.logger {}
    private val client =
        GraphQLKtorClient(
            url = URL("${Configuration.hmdb.grunndataApi}/graphql"),
            httpClient = HttpClient(engineFactory = Apache),
            serializer = GraphQLClientJacksonSerializer()
        )

    suspend fun hentIsokortnavnForProdukterMedHmsnrs(hmsnrs: Set<String>): List<Produkt> {
        if (hmsnrs.isEmpty()) return emptyList()
        logg.debug { "Henter isokortnavn for produkter med hmsnrs=$hmsnrs fra hjelpemiddeldatabasen" }
        val request = HentIsokortnavnForProdukterMedHmsnrs(variables = HentIsokortnavnForProdukterMedHmsnrs.Variables(hmsnrs = hmsnrs.toList()))
        return try {
            val response = client.execute(request)
            when {
                response.errors != null -> {
                    logg.error { "Feil under henting av data fra hjelpemiddeldatabasen, hmsnrs=$hmsnrs, errors=${response.errors?.map { it.message }}" }
                    throw Exception("Feil under henting av data fra hjelpemiddeldatabasen, hmsnrs=$hmsnrs, errors=${response.errors?.map { it.message }}")
                }
                response.data != null -> {
                    val produkter = response.data?.produkter ?: emptyList()
                    logg.debug { "Hentet ${produkter.size} isokortnavn for produkter fra hjelpemiddeldatabasen" }
                    produkter
                }
                else -> throw Exception("Unexpected response: $response")
            }
        } catch (e: Exception) {
            logg.error(e) { "Feil under henting av data fra hjelpemiddeldatabasen, hmsnrs=$hmsnrs" }
            throw Exception("Feil under henting av data fra hjelpemiddeldatabasen, hmsnrs=$hmsnrs")
        }
    }
}