package no.nav.dagpenger.soknad.mottak

import io.ktor.application.Application
import io.ktor.application.install
import io.ktor.features.CallLogging
import io.ktor.request.path
import io.ktor.routing.route
import io.ktor.routing.routing
import no.nav.dagpenger.soknad.mottak.db.SoknadStore
import no.nav.dagpenger.soknad.mottak.db.SoknadStorePostgres
import no.nav.dagpenger.soknad.mottak.db.dataSourceFrom
import no.nav.dagpenger.soknad.mottak.db.migrate
import no.nav.dagpenger.soknad.mottak.oppslag.PDLClient
import no.nav.dagpenger.soknad.mottak.oppslag.StsClient
import no.nav.dagpenger.soknad.mottak.service.JournalPostSink
import no.nav.dagpenger.soknad.mottak.service.SoknadDataSink
import no.nav.dagpenger.soknad.mottak.service.getFagsakId
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import org.slf4j.event.Level

fun main() {
    val store = SoknadStorePostgres(dataSourceFrom(Configuration))

    val pdlClient = PDLClient(
        Configuration.pdl.baseUrl,
        StsClient(
            Configuration.sts.baseUrl,
            Configuration.sts.user,
            Configuration.sts.password
        )
    )

    RapidApplication.Builder(RapidApplication.RapidApplicationConfig.fromEnv(Configuration.rapidApplication))
        .withKtorModule {
            api(store)
        }
        .build().apply {
            JournalPostSink(this, store)
            SoknadDataSink(this, store, pdlClient)
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
            getFagsakId(store)
        }
    }
}
