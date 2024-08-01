package no.nav.hjelpemidler.soknad.mottak.river

import com.fasterxml.jackson.databind.JsonNode
import no.nav.helse.rapids_rivers.JsonMessage
import java.util.UUID

fun JsonMessage.uuidValue(key: String): UUID = get(key).uuidValue()

fun JsonNode.uuidValue(): UUID = textValue().let(UUID::fromString)
