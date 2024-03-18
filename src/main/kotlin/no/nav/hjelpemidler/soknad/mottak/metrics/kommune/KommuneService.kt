package no.nav.hjelpemidler.soknad.mottak.metrics.kommune


class KommuneService(private val oppslagClient: OppslagClient = CachedOppslagClient()) {

    suspend fun kommunenrTilSted(kommunenr: String?): KommuneDto? {
        val kommuner = oppslagClient.hentAlleKommuner()
        return kommuner[kommunenr]
    }
}
