package no.nav.hjelpemidler.soknad.mottak.river

import no.nav.helse.rapids_rivers.JsonMessage
import java.util.UUID

fun JsonMessage.uuidValue(key: String): UUID = get(key).textValue().let(UUID::fromString)
