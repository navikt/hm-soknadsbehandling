package no.nav.hjelpemidler.soknad.mottak.river

import no.nav.helse.rapids_rivers.JsonMessage

interface Melding {
    fun toJsonMessage(eventName: String): JsonMessage
}
