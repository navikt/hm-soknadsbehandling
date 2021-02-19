package no.nav.hjelpemidler.soknad.mottak.service

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.util.pipeline.*
import no.nav.hjelpemidler.soknad.mottak.db.SoknadStore

internal fun Route.getFagsakId(store: SoknadStore) {
        get("/soknad/bruker/{soknadsId}") {
            val soknadsId = soknadsId()
            soknadsId?.let {
                try {
                    store.hentSoknad(it).let { soknad ->
                                if (soknad != null) {
                                    // TODO returnere noe fornuftig her
                                } else {
                                    call.respond(HttpStatusCode.NotFound)
                                }
                            }
                } catch (e: Exception) {
                   //logger.error(e) { "Unable to find fagsakId for søknadsId: $soknadsId" }
                    call.respond(HttpStatusCode.InternalServerError)
                }
            }
        }
    }

private fun PipelineContext<Unit, ApplicationCall>.soknadsId() =
        call.parameters["soknadsId"]
