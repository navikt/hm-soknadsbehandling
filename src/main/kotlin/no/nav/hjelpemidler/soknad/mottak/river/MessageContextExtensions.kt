package no.nav.hjelpemidler.soknad.mottak.river

import com.fasterxml.jackson.module.kotlin.convertValue
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.MessageProblems
import no.nav.hjelpemidler.soknad.mottak.jsonMapper

fun <T : Any> MessageContext.publish(key: String, message: T): Unit =
    publish(key, jsonMessageOf(message).toJson())

private fun jsonMessageOf(value: Any): JsonMessage =
    JsonMessage("{}", MessageProblems("")).apply {
        jsonMapper
            .convertValue<Map<String, Any?>>(value)
            .mapNotNull { (key, value) -> if (value == null) null else key to value }
            .forEach { (key, value) -> this[key] = value }
    }
