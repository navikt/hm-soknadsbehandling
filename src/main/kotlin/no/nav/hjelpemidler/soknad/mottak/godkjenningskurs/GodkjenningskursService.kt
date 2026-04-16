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

    suspend fun oppdaterPersoninfo(behovsmelding: JsonNode, fnrInnsender: String) {

        log.info { "Oppdaterer personinfo" }

        val formidler = behovsmelding["levering"]["hjelpemiddelformidler"]

        val personinfo = Personinfo(
            fnr = Fødselsnummer(fnrInnsender),
            navn = jsonMapper.convertValue<Personnavn>(formidler["navn"]),
            epost = formidler["epost"].textValue(),
            arbeidssted = formidler["arbeidssted"].textValue(),
        )

        client.oppdaterPersoninfo(personinfo)

        log.info { "Personinfo oppdatert" }
    }
}