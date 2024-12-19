package no.nav.hjelpemidler.soknad.mottak.river

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.hjelpemidler.behovsmeldingsmodell.BehovsmeldingStatus
import no.nav.hjelpemidler.behovsmeldingsmodell.sak.HotsakSakId
import no.nav.hjelpemidler.soknad.mottak.logging.sikkerlogg
import no.nav.hjelpemidler.soknad.mottak.melding.OrdrelinjeLagretMelding
import no.nav.hjelpemidler.soknad.mottak.metrics.Prometheus
import no.nav.hjelpemidler.soknad.mottak.soknadsbehandling.SøknadsbehandlingService
import java.util.UUID

private val logger = KotlinLogging.logger {}

class NyHotsakOrdrelinje(
    rapidsConnection: RapidsConnection,
    private val søknadsbehandlingService: SøknadsbehandlingService,
) : NyOrdrelinje(), AsyncPacketListener {
    init {
        River(rapidsConnection).apply {
            precondition { it.requireValue("eventName", "hm-NyOrdrelinje-hotsak") }
            validate { it.requireKey("eventId", "opprettet", "fnrBruker", "data") }
        }.register(this)
    }

    private val JsonMessage.sakId get() = this["data"]["saksnummer"].textValue()

    override suspend fun onPacketAsync(packet: JsonMessage, context: MessageContext) {
        val eventId = packet.eventId
        val sakId = packet.sakId
        if (sakId.isEmpty()) {
            logger.info { "Hopper over event med ugyldig Hotsak-sakId = '', eventId: $eventId" }
            sikkerlogg.error { "Hopper over event med ugyldig Hotsak-sakId = '', eventId: $eventId, packet: '${packet.toJson()}'" }
            return
        }
        if (eventId in skipList) {
            logger.info { "Hopper over event i skipList: $eventId" }
            sikkerlogg.error { "Hopper over event i skipList, packet: ${packet.toJson()}" }
            return
        }
        try {
            logger.info { "Hotsak-ordrelinje fra OEBS mottatt med eventId: $eventId, sakId: $sakId" }

            // Finn søknad for Hotsak-sak
            val søknad = søknadsbehandlingService.finnSøknadForSak(HotsakSakId(sakId))
            if (søknad == null) {
                logger.warn { "Ordrelinje med eventId: $eventId og sakId: $sakId kan ikke matches mot en søknadId" }
                return
            }
            val søknadId = søknad.søknadId

            logger.info { "Fant søknadId: $søknadId fra Hotsak-sakId: $sakId" }

            val fnrBruker = packet.fnrBruker
            val data = packet.data
            val innkommendeOrdrelinje = packet.innkommendeOrdrelinje
            val ordrelinje = innkommendeOrdrelinje.tilOrdrelinje(søknadId, fnrBruker, data)

            // fixme -> kunne vi ikke sjekket dette i API-et og oppdatert status der? Hvorfor gjøre dette i en dialog med API-et?
            val lagret = søknadsbehandlingService.lagreOrdrelinje(ordrelinje)
            if (!lagret) {
                return
            }
            søknadsbehandlingService.oppdaterStatus(søknadId, BehovsmeldingStatus.UTSENDING_STARTET)

            context.publish(
                ordrelinje.fnrBruker,
                data + mapOf<String, Any?>(
                    "eventId" to UUID.randomUUID(),
                    "eventName" to "hm-OrdrelinjeMottatt",
                    "opprettet" to packet.opprettet,
                    "søknadId" to søknadId,
                    "behovsmeldingType" to søknad.behovsmeldingstype,
                )
            )

            if (ordrelinje.forDel) {
                logger.info { "Ordrelinje for 'Del' lagret, søknadId: $søknadId" }
                // Vi skal ikke agere ytterligere på disse
                return
            }

            val ordreSisteDøgn = søknadsbehandlingService.ordreSisteDøgn(søknadId)
            if (!ordreSisteDøgn.harOrdreAvTypeHjelpemidler) {
                context.publish(fnrBruker, OrdrelinjeLagretMelding(ordrelinje, søknad.behovsmeldingstype))
                Prometheus.ordrelinjeVideresendtCounter.increment()
                logger.info { "Ordrelinje sendt, søknadId: $søknadId" }
                sikkerlogg.info { "Ordrelinje sendt, søknadId: $søknadId, fnrBruker: $fnrBruker" }
            } else {
                logger.info { "Ordrelinje mottatt, men varsel til bruker er allerede sendt ut det siste døgnet: $søknadId" }
            }
        } catch (e: Exception) {
            logger.error(e) { "Håndtering av eventId: $eventId feilet" }
            throw e
        }
    }

    private val skipList = listOf<UUID>()
}
