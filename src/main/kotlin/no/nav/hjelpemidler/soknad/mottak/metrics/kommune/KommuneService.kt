package no.nav.hjelpemidler.soknad.mottak.metrics.kommune

import kotlinx.coroutines.runBlocking

class KommuneService(oppslagClient: OppslagClient = OppslagClient()) {

    private val kommunenrTilSted: Map<String, KommuneDto> = runBlocking { oppslagClient.hentAlleKommuner() }

    fun kommunenrTilSted(kommunenr: String?): KommuneDto? {
        return kommunenrTilSted[kommunenr]
    }
}
