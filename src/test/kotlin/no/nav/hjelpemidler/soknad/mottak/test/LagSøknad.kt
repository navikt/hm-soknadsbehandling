package no.nav.hjelpemidler.soknad.mottak.test

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.NullNode
import no.nav.hjelpemidler.behovsmeldingsmodell.BehovsmeldingStatus
import no.nav.hjelpemidler.behovsmeldingsmodell.BehovsmeldingType
import no.nav.hjelpemidler.behovsmeldingsmodell.SøknadId
import no.nav.hjelpemidler.soknad.mottak.client.Søknad
import no.nav.hjelpemidler.soknad.mottak.jsonMapper
import org.intellij.lang.annotations.Language
import java.time.Instant

fun lagSøknad(
    søknadId: SøknadId = SøknadId.randomUUID(),
    status: BehovsmeldingStatus = BehovsmeldingStatus.VENTER_GODKJENNING,
    data: JsonNode = NullNode.getInstance(),
): Søknad {
    val now = Instant.now()
    return Søknad(
        søknadId = søknadId,
        søknadOpprettet = now,
        søknadEndret = now,
        søknadGjelder = "Søknad om hjelpemidler",
        fnrInnsender = "12345678910",
        fnrBruker = "01987654321",
        navnBruker = "fornavn etternavn",
        kommunenavn = "kommunenavn",
        journalpostId = null,
        oppgaveId = null,
        digital = true,
        behovsmeldingstype = BehovsmeldingType.SØKNAD,
        status = status,
        statusEndret = now,
        data = data,
    )
}

fun lagSøknad(
    søknadId: SøknadId = SøknadId.randomUUID(),
    status: BehovsmeldingStatus = BehovsmeldingStatus.VENTER_GODKJENNING,
    @Language("JSON") data: String,
): Søknad = lagSøknad(søknadId, status, jsonMapper.readTree(data))
