package no.nav.hjelpemidler.soknad.mottak.oppslag

import com.github.kittinunf.fuel.core.ResponseDeserializable
import com.github.kittinunf.fuel.core.extensions.authentication
import com.github.kittinunf.fuel.coroutines.awaitObject
import com.github.kittinunf.fuel.httpGet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import no.nav.hjelpemidler.soknad.mottak.serder.ObjectMapper
import java.time.LocalDateTime

private val logger = KotlinLogging.logger {}

internal data class Token(val value: String, val ttl: Long, val createdTime: LocalDateTime = LocalDateTime.now()) {
    val expireTime: LocalDateTime = createdTime.plusSeconds(ttl)
    val expired: Boolean
        get() = LocalDateTime.now().plusMinutes(10).isAfter(this.expireTime)

    companion object {
        fun isValid(token: Token?) = when (token) {
            null -> false
            else -> !token.expired
        }
    }
}

internal class StsClient(private val baseUrl: String, private val username: String, private val password: String) {
    private var token: Token? = null
    private val mutex = Mutex()

    suspend fun getToken(): String = mutex.withLock {
        if (!Token.isValid(token)) {
            token = fetchToken()
        }
        token!!.value
    }

    private suspend fun fetchToken(): Token =
        withContext(Dispatchers.IO) {
            kotlin.runCatching {
                "$baseUrl/$PATH".httpGet()
                    .authentication().basic(username, password)
                    .header(
                        "Accept",
                        "application/json"
                    )
                    .also { logger.debug { "Request: $it" } }
                    .awaitObject(responseDeserializer)
            }
                .onSuccess { logger.info { "Retrieved token with expire time: ${it.expireTime}" } }
                .onFailure { logger.error { "Failed to retrieve token ${it.message}" } }
                .getOrThrow()
        }

    companion object {
        const val PATH = "rest/v1/sts/token?grant_type=client_credentials&scope=openid"
        val responseDeserializer = object : ResponseDeserializable<Token> {
            override fun deserialize(content: String): Token {
                return ObjectMapper.instance.readTree(content).let {
                    Token(
                        it["access_token"].textValue(),
                        it["expires_in"].longValue()
                    )
                }
            }
        }
    }
}
