package no.nav.hjelpemidler.soknad.mottak.test

import com.fasterxml.jackson.databind.JsonNode
import no.nav.hjelpemidler.soknad.mottak.jsonMapper
import org.intellij.lang.annotations.Language

object Testdata {
    val testmeldingerFraOebs = jsonMapper.readResource<List<JsonNode>>("/kafka_testmessages/testmeldingerFraOebs.json")
}

fun readTree(@Language("JSON") content: String): JsonNode = jsonMapper.readTree(content)

@JvmInline
value class Json(@Language("JSON") private val content: String) : CharSequence by content {
    override fun toString(): String = content
}
