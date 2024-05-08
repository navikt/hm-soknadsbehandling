package no.nav.hjelpemidler.soknad.mottak

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.apache.Apache
import io.ktor.client.plugins.HttpTimeout
import no.nav.hjelpemidler.http.jackson

fun httpClient(block: HttpClientConfig<*>.() -> Unit = {}): HttpClient = HttpClient(Apache) {
    expectSuccess = true
    jackson(jsonMapper)
    install(HttpTimeout)
    block()
}
