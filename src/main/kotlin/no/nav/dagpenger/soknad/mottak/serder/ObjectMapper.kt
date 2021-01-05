package no.nav.dagpenger.soknad.mottak.serder

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule

object ObjectMapper {
    val instance = ObjectMapper().registerModules(
        JavaTimeModule(),
        KotlinModule()
    )
}
