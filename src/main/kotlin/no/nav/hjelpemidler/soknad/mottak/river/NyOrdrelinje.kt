package no.nav.hjelpemidler.soknad.mottak.river

import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.annotation.JsonAnySetter
import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import no.nav.hjelpemidler.behovsmeldingsmodell.BehovsmeldingId
import no.nav.hjelpemidler.behovsmeldingsmodell.ordre.Ordrelinje
import no.nav.hjelpemidler.soknad.mottak.asObject

abstract class NyOrdrelinje {
    protected val JsonMessage.eventId get() = uuidValue("eventId")
    protected val JsonMessage.opprettet get() = this["opprettet"].textValue()
    protected val JsonMessage.fnrBruker get() = this["fnrBruker"].textValue()
    protected val JsonMessage.data get() = this["data"].asObject<Map<String, Any?>>()
    protected val JsonMessage.innkommendeOrdrelinje get() = this["data"].asObject<InnkommendeOrdrelinje>()

    /**
     * Ordrelinje fra OEBS via hm-oebs-listener
     */
    data class InnkommendeOrdrelinje(
        val oebsId: Int,
        val serviceforespørsel: Int?,
        val ordrenr: Int,
        val ordrelinje: Int,
        val delordrelinje: Int,
        val artikkelnr: String,
        val antall: Double,
        val enhet: String,
        val produktgruppe: String,
        @JsonAlias("produktgruppeNr")
        val produktgruppenr: String,
        val hjelpemiddeltype: String,
    ) {
        @JsonAnySetter
        val andre = mutableMapOf<String, Any?>()

        /**
         * Transformer ordrelinje fra OEBS til ordrelinje for lagring i hm-soknadsbehandling-db
         */
        fun tilOrdrelinje(søknadId: BehovsmeldingId, fnrBruker: String, data: Map<String, Any?>): Ordrelinje = Ordrelinje(
            søknadId = søknadId,
            oebsId = oebsId,
            fnrBruker = fnrBruker,
            serviceforespørsel = serviceforespørsel,
            ordrenr = ordrenr,
            ordrelinje = ordrelinje,
            delordrelinje = delordrelinje,
            artikkelnr = artikkelnr,
            antall = antall,
            enhet = enhet,
            produktgruppe = produktgruppe,
            produktgruppenr = produktgruppenr,
            hjelpemiddeltype = hjelpemiddeltype,
            data = data,
        )
    }
}
