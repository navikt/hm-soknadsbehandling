package no.nav.hjelpemidler.soknad.mottak

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.treeToValue
import no.nav.hjelpemidler.serialization.jackson.jsonMapper

inline fun <reified T> JsonNode.asObject(): T = jsonMapper.treeToValue(this)
