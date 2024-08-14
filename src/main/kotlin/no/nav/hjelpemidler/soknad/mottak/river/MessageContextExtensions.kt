package no.nav.hjelpemidler.soknad.mottak.river

import no.nav.helse.rapids_rivers.MessageContext
import no.nav.hjelpemidler.soknad.mottak.jsonMapper

fun <T : Any> MessageContext.publish(key: String, message: T): Unit =
    publish(key, jsonMapper.writeValueAsString(message))

fun <T : Melding> MessageContext.publish(key: String, message: T, eventName: String): Unit =
    publish(key, message.toJsonMessage(eventName).toJson())
