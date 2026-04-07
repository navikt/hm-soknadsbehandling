package no.nav.hjelpemidler.soknad.mottak.godkjenningskurs

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.convertValue
import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.hjelpemidler.domain.person.Fødselsnummer
import no.nav.hjelpemidler.domain.person.Personnavn
import no.nav.hjelpemidler.serialization.jackson.jsonMapper
import no.nav.hjelpemidler.soknad.mottak.client.GodkjenningskursClient
import no.nav.hjelpemidler.soknad.mottak.client.Personinfo

private val log = KotlinLogging.logger { }

class GodkjenningskursService(
    private val client: GodkjenningskursClient
) {

    private val JsonNode.fnrInnsender get() = this["fodselNrInnsender"].textValue()

    suspend fun oppdaterPersoninfo(behovsmelding: JsonNode) {

        log.info { "Oppdaterer personinfo" }

        val formidler = behovsmelding["levering"]["hjelpemiddelformidler"]

        val personinfo = Personinfo(
            fnr = Fødselsnummer(behovsmelding.fnrInnsender),
            navn = jsonMapper.convertValue<Personnavn>(formidler["navn"]),
            epost = formidler["epost"].textValue(),
            arbeidssted = formidler["arbeidssted"].textValue(),
            kommunenummer = formidler["kommunenummer"].textValue(),
        )

        client.oppdaterPersoninfo(personinfo)

        log.info { "Personinfo oppdatert" }
    }
}