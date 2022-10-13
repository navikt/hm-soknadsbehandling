package no.nav.hjelpemidler.soknad.mottak.test

import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.module.kotlin.readValue

inline fun <reified T> JsonMapper.readResource(name: String) = requireNotNull(javaClass.getResourceAsStream(name)).use {
    readValue<T>(it)
}
