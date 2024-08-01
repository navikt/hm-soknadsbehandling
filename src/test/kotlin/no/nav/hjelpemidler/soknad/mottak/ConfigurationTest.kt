package no.nav.hjelpemidler.soknad.mottak

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.github.jknack.handlebars.Handlebars
import com.github.jknack.handlebars.io.FileTemplateLoader
import io.kotest.matchers.collections.shouldContainAll
import no.nav.hjelpemidler.configuration.environmentVariablesIn
import kotlin.test.Test

class ConfigurationTest {
    @Test
    fun `Har definert miljøvariabler for dev`() {
        environmentVariablesIn("nais-dev") shouldContainAll environmentVariablesIn(Configuration)
    }

    @Test
    fun `Har definert miljøvariabler for prod`() {
        environmentVariablesIn("nais-prod") shouldContainAll environmentVariablesIn(Configuration)
    }


    private fun environmentVariablesIn(location: String): List<String> {
        val manifest = handlebars.compile(location).apply(mapOf("image" to "test"))
        return mapper.readValue<JsonNode>(manifest)
            .at("/spec/env")
            .map { it["name"].textValue() }
            .sorted()
    }
}

private val handlebars: Handlebars by lazy {
    Handlebars(FileTemplateLoader(".nais", ".yaml"))
}

private val mapper: YAMLMapper by lazy {
    YAMLMapper.builder()
        .addModule(kotlinModule())
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .build()
}
