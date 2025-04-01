package no.nav.hjelpemidler.soknad.mottak.river

import com.fasterxml.jackson.module.kotlin.convertValue
import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageProblems
import no.nav.hjelpemidler.serialization.jackson.jsonMapper
import no.nav.hjelpemidler.soknad.mottak.metrics.Prometheus

fun <T : Any> MessageContext.publish(key: String, message: T): Unit =
    publish(key, jsonMessageOf(message).toJson())

private fun jsonMessageOf(value: Any): JsonMessage =
    JsonMessage("{}", MessageProblems("")).apply {
        jsonMapper
            .convertValue<Map<String, Any?>>(value)
            .mapNotNull { (key, value) -> if (value == null) null else key to value }
            .forEach { (key, value) -> this[key] = value }
    }
