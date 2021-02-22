package no.nav.hjelpemidler.soknad.mottak

import io.ktor.application.Application
import io.ktor.application.install
import io.ktor.auth.authenticate
import io.ktor.features.CallLogging
import io.ktor.features.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.jackson.JacksonConverter
import io.ktor.request.path
import io.ktor.routing.route
import io.ktor.routing.routing
import kotlinx.coroutines.runBlocking
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.hjelpemidler.soknad.mottak.db.SoknadStore
import no.nav.hjelpemidler.soknad.mottak.db.SoknadStorePostgres
import no.nav.hjelpemidler.soknad.mottak.db.dataSourceFrom
import no.nav.hjelpemidler.soknad.mottak.db.migrate
import no.nav.hjelpemidler.soknad.mottak.service.SoknadDataSink
import no.nav.hjelpemidler.soknad.mottak.service.SoknadMedFullmaktDataSink
import no.nav.hjelpemidler.soknad.mottak.service.SoknadUtenFullmaktDataSink
import no.nav.hjelpemidler.soknad.mottak.service.hentSoknad
import org.slf4j.event.Level

fun main() {
    val store = SoknadStorePostgres(dataSourceFrom(Configuration))

    RapidApplication.Builder(RapidApplication.RapidApplicationConfig.fromEnv(Configuration.rapidApplication))
        .withKtorModule { api(store) }
        .build().apply {
            SoknadDataSink(this, store)
        }.apply {
            SoknadMedFullmaktDataSink(this, store)
        }.apply {
            SoknadUtenFullmaktDataSink(this, store)
        }.apply {
            register(
                object : RapidsConnection.StatusListener {
                    override fun onStartup(rapidsConnection: RapidsConnection) {
                        migrate(Configuration)
                    }
                }
            )
        }.start()
}

internal fun Application.api(store: SoknadStore) {

    install(CallLogging) {
        level = Level.INFO
        filter { it.request.path().startsWith("/api") }
    }

    install(ContentNegotiation) {
        register(ContentType.Application.Json, JacksonConverter(JacksonMapper.objectMapper))
    }

    val config = runBlocking { environment.config.load() }

    installAuthentication(config)

    routing {
        authenticate("tokenX") {
            route("/api") {
                hentSoknad(store)
            }
        }
    }
}
