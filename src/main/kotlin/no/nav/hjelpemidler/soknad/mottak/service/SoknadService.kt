package no.nav.hjelpemidler.soknad.mottak.service

import io.ktor.application.ApplicationCall
import io.ktor.application.call
import io.ktor.auth.principal
import io.ktor.http.HttpStatusCode
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.get
import io.ktor.util.pipeline.PipelineContext
import mu.KotlinLogging
import no.nav.hjelpemidler.soknad.mottak.UserPrincipal
import no.nav.hjelpemidler.soknad.mottak.db.SoknadStore
import java.util.UUID

private val logger = KotlinLogging.logger {}

internal fun Route.hentSoknad(store: SoknadStore) {
    get("/soknad/bruker/{soknadsId}") {
        try {
            val soknadsId = UUID.fromString(soknadsId())
            val fnr = call.principal<UserPrincipal>()?.getFnr()
                ?: call.respond(HttpStatusCode.BadRequest, "Fnr mangler i token claim")
            val soknad = store.hentSoknad(soknadsId)

            when {
                soknad == null -> {
                    call.respond(HttpStatusCode.NotFound)
                }
                soknad.bruker.fnummer != fnr -> {
                    call.respond(HttpStatusCode.Forbidden, "Søknad er ikke registrert på aktuell bruker")
                }
                else -> {
                    call.respond(soknad)
                }
            }
        } catch (e: Exception) {
            call.respond(HttpStatusCode.BadRequest, "Feil ved henting av søknad ${e.message}")
        }
    }
}

internal fun Route.hentSoknaderTilGodkjenning(store: SoknadStore) {
    get("/soknad/bruker") {

        val fnr = call.principal<UserPrincipal>()?.getFnr() ?: throw RuntimeException("Fnr mangler i token claim")

        try {
            val soknaderTilGodkjenning = store.hentSoknaderTilGodkjenning(fnr)

            call.respond(soknaderTilGodkjenning)
        } catch (e: Exception) {
            logger.error(e) { "Error on fetching søknader til godkjenning" }
            call.respond(HttpStatusCode.InternalServerError, e)
        }
    }
}

private fun PipelineContext<Unit, ApplicationCall>.soknadsId() =
    call.parameters["soknadsId"]
