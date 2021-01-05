package no.nav.dagpenger.soknad.mottak.service

import io.ktor.application.ApplicationCall
import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.response.respond
import io.ktor.response.respondText
import io.ktor.routing.Route
import io.ktor.routing.get
import io.ktor.util.pipeline.PipelineContext
import mu.KotlinLogging
import no.nav.dagpenger.soknad.mottak.db.SoknadStore

private val logger = KotlinLogging.logger {}

internal fun Route.getFagsakId(store: SoknadStore) {
    get("/fagsakid/{soknadsId}") {
        val soknadsId = søknadsId()
        soknadsId?.let {
            try {
                store.findFagsakId(it).let { id ->
                    if (id != null) {
                        call.respondText(text = id, status = HttpStatusCode.OK)
                    } else {
                        call.respond(HttpStatusCode.NotFound)
                    }
                }
            } catch (e: Exception) {
                logger.error(e) { "Unable to find fagsakId for søknadsId: $soknadsId" }
                call.respond(HttpStatusCode.InternalServerError)
            }
        }
    }

    get("/journalpost/{soknadsId}") {
        val soknadsId = søknadsId()
        soknadsId?.let {
            try {
                store.findJournalpostId(it).let { id ->
                    if (id != null) {
                        call.respondText(text = id, status = HttpStatusCode.OK)
                    } else {
                        call.respond(HttpStatusCode.NotFound)
                    }
                }
            } catch (e: Exception) {
                logger.error(e) { "Unable to find journalpost for søknadsId: $soknadsId" }
                call.respond(HttpStatusCode.InternalServerError)
            }
        }
    }
}

private fun PipelineContext<Unit, ApplicationCall>.søknadsId() =
    call.parameters["soknadsId"]
