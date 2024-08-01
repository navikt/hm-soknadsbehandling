package no.nav.hjelpemidler.soknad.mottak.soknadsbehandling

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageProblems
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.hjelpemidler.soknad.mottak.client.SøknadForRiverClient
import no.nav.hjelpemidler.soknad.mottak.metrics.Prometheus
import java.time.LocalDateTime
import java.util.UUID

class SøknadsgodkjenningService(
    private val søknadForRiverClient: SøknadForRiverClient,
    private val rapidsConnection: RapidsConnection,
) {
    suspend fun slettUtgåtteSøknader(): Int {
        val utgåtteSøknader = søknadForRiverClient.hentSøknaderTilGodkjenningEldreEnn(FEMTEN_DAGER)
        utgåtteSøknader.forEach { søknad ->
            val antallOppdatert = søknadForRiverClient.slettUtløptSøknad(søknad.søknadId!!)

            if (antallOppdatert > 0) {
                val søknadErUtgåttMessage = JsonMessage("{}", MessageProblems("")).also {
                    it["eventId"] = UUID.randomUUID()
                    it["eventName"] = "hm-GodkjenningsfristErUtløpt"
                    it["opprettet"] = LocalDateTime.now()
                    it["fnrBruker"] = søknad.fnrBruker!!
                    it["søknadId"] = søknad.søknadId
                }.toJson()

                rapidsConnection.publish(søknad.fnrBruker!!, søknadErUtgåttMessage)
                Prometheus.godkjenningsfristErUtløptCounter.inc()
            }
        }

        return utgåtteSøknader.size
    }

    companion object {
        const val EN_UKE = 7
        const val FEMTEN_DAGER = 15
    }
}
