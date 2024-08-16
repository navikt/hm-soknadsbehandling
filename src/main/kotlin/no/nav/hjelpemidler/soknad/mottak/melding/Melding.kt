package no.nav.hjelpemidler.soknad.mottak.melding

import java.util.UUID

interface Melding {
    val eventId: UUID
    val eventName: String
}
