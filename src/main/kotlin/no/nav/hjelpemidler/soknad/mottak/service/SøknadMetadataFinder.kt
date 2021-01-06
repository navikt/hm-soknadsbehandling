package no.nav.hjelpemidler.soknad.mottak.service

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.util.pipeline.*
import mu.KotlinLogging
import no.nav.hjelpemidler.soknad.mottak.db.SoknadStore

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
