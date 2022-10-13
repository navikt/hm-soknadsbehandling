package no.nav.hjelpemidler.soknad.mottak

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonMapperBuilder
import com.fasterxml.jackson.module.kotlin.treeToValue
import no.nav.helse.rapids_rivers.MessageContext

val jsonMapper: JsonMapper = jacksonMapperBuilder()
    .addModule(JavaTimeModule())
    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
    .build()

class JacksonMapper {
    companion object {
        val objectMapper: JsonMapper = jsonMapper
    }
}

inline fun <reified T> JsonNode.asObject(): T =
    jsonMapper.treeToValue(this)

fun <T : Any> MessageContext.publish(key: String, message: T): Unit =
    publish(key, jsonMapper.writeValueAsString(message))
