package no.nav.hjelpemidler.soknad.mottak

import com.auth0.jwk.JwkProviderBuilder
import io.ktor.application.Application
import io.ktor.application.install
import io.ktor.auth.Authentication
import io.ktor.auth.Principal
import io.ktor.auth.jwt.jwt
import java.net.URL
import java.util.concurrent.TimeUnit

internal fun Application.installAuthentication(config: TokenXConfig, applicationConfig: Configuration) {

    val jwkProvider = JwkProviderBuilder(URL(config.metadata.jwksUri))
        // cache up to 10 JWKs for 24 hours
        .cached(10, 24, TimeUnit.HOURS)
        // if not cached, only allow max 10 different keys per minute to be fetched from external provider
        .rateLimited(10, 1, TimeUnit.MINUTES)
        .build()

    install(Authentication) {
        jwt("tokenX") {
            verifier(jwkProvider, config.metadata.issuer)
            validate { credentials ->
                requireNotNull(credentials.payload.audience) {
                    "Auth: Missing audience in token"
                }
                require(credentials.payload.audience.contains(config.clientId)) {
                    "Auth: Valid audience not found in claims"
                }

                require(credentials.payload.getClaim("acr").asString() == ("Level4")) { "Auth: Level4 required" }
                UserPrincipal(credentials.payload.getClaim(applicationConfig.application.userclaim).asString())
            }
        }
    }
}

internal class UserPrincipal(private val fnr: String) : Principal {
    fun getFnr() = fnr
}
