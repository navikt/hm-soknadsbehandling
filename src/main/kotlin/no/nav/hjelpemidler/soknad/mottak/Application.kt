package no.nav.hjelpemidler.soknad.mottak

import io.ktor.application.*
import io.ktor.features.*
import io.ktor.request.*
import io.ktor.routing.*
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.hjelpemidler.soknad.mottak.db.SoknadStore
import no.nav.hjelpemidler.soknad.mottak.db.SoknadStorePostgres
import no.nav.hjelpemidler.soknad.mottak.db.dataSourceFrom
import no.nav.hjelpemidler.soknad.mottak.db.migrate
import no.nav.hjelpemidler.soknad.mottak.service.SoknadDataSink
import no.nav.hjelpemidler.soknad.mottak.service.SoknadMedFullmaktDataSink
import no.nav.hjelpemidler.soknad.mottak.service.SoknadUtenFullmaktDataSink
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

        routing {
            route("/api") {

            }
        }
}
