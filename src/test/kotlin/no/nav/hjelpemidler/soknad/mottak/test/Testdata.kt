package no.nav.hjelpemidler.soknad.mottak.test

import com.fasterxml.jackson.databind.JsonNode
import no.nav.hjelpemidler.soknad.mottak.jsonMapper

object Testdata {
    val testmeldingerFraOebs = jsonMapper.readResource<List<JsonNode>>("/kafka_testmessages/testmeldingerFraOebs.json")
}
