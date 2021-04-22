package no.nav.hjelpemidler.soknad.mottak.tokenx

import no.nav.tms.token.support.tokendings.exchange.TokendingsService

class TokendingsServiceWrapper(
    private val tokendingsService: TokendingsService,
    private val soknadsbehandlingDbClientId: String
) {
    suspend fun exchangeTokenForSoknadsbehandlingDb(token: String): AccessToken {
        return AccessToken(tokendingsService.exchangeToken(token, soknadsbehandlingDbClientId))
    }
}
