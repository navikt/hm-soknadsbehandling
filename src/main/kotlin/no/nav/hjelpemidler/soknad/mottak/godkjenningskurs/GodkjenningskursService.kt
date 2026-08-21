package no.nav.hjelpemidler.soknad.mottak.godkjenningskurs

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.hjelpemidler.behovsmeldingsmodell.BehovsmeldingType
import no.nav.hjelpemidler.domain.person.Fødselsnummer
import no.nav.hjelpemidler.domain.person.Personnavn
import no.nav.hjelpemidler.serialization.jackson.jsonMapper
import no.nav.hjelpemidler.soknad.mottak.client.GodkjenningskursClient
import no.nav.hjelpemidler.soknad.mottak.client.Personinfo
import tools.jackson.databind.JsonNode
import tools.jackson.module.kotlin.convertValue
import java.util.UUID

private val log = KotlinLogging.logger { }

class GodkjenningskursService(
    private val client: GodkjenningskursClient
) {
    suspend fun oppdaterPersoninfo(behovsmeldingId: UUID, behovsmelding: JsonNode, fnrInnsender: String) {
        val behovsmeldingType = BehovsmeldingType.valueOf(behovsmelding["type"].stringValue())
        if (behovsmeldingType == BehovsmeldingType.BRUKERPASSBYTTE) {
            log.warn { "Oppdaterer ikke personinfo da behovsmeldingType == $behovsmeldingType, behovsmeldingId: $behovsmeldingId" }
            return
        }

        log.info { "Oppdaterer personinfo" }

        val formidler = behovsmelding["levering"]["hjelpemiddelformidler"]

        val personinfo = Personinfo(
            fnr = Fødselsnummer(fnrInnsender),
            navn = jsonMapper.convertValue<Personnavn>(formidler["navn"]),
            epost = formidler["epost"].stringValue(),
            arbeidssted = formidler["arbeidssted"].stringValue(),
        )

        client.oppdaterPersoninfo(personinfo)

        log.info { "Personinfo oppdatert" }
    }
}
