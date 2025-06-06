package no.nav.hjelpemidler.soknad.mottak.test

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.hjelpemidler.serialization.jackson.jsonMapper
import no.nav.hjelpemidler.serialization.jackson.readResourceAsValue
import org.intellij.lang.annotations.Language

object Testdata {
    val testmeldingerFraOebs =
        jsonMapper.readResourceAsValue<List<JsonNode>>("/kafka_testmessages/testmeldingerFraOebs.json")
}

fun readMap(@Language("JSON") content: String): Map<String, Any?> = jsonMapper.readValue(content)

@JvmInline
value class Json(@Language("JSON") private val content: String) : CharSequence by content {
    override fun toString(): String = content
}
