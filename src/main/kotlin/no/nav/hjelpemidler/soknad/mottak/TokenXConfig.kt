package no.nav.hjelpemidler.soknad.mottak

import com.fasterxml.jackson.annotation.JsonProperty
import io.ktor.client.request.get
import io.ktor.config.ApplicationConfig
import io.ktor.util.KtorExperimentalAPI

data class TokenXConfig(
    val metadata: Metadata,
    val clientId: String,
) {
    data class Metadata(
        @JsonProperty("issuer") val issuer: String,
        @JsonProperty("jwks_uri") val jwksUri: String,
    )
}

@KtorExperimentalAPI
suspend fun ApplicationConfig.load(): TokenXConfig {

    val jwksUri = propertyOrNull("TOKEN_X_WELL_KNOWN_URL")?.getString() ?: "http://host.docker.internal:8080/default/.well-known/openid-configuration"
    val clientId = propertyOrNull("TOKEN_X_CLIENT_ID")?.getString() ?: "local:hjelpemidlerdigitalsoknad-api"

    return TokenXConfig(
        metadata = httpClient().get(jwksUri),
        clientId = clientId
    )
}
