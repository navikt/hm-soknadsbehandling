package no.nav.hjelpemidler.soknad.mottak.river

import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.annotation.JsonAnySetter
import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import no.nav.hjelpemidler.behovsmeldingsmodell.BehovsmeldingId
import no.nav.hjelpemidler.behovsmeldingsmodell.ordre.Ordrelinje
import no.nav.hjelpemidler.serialization.jackson.stringValueOrNull
import no.nav.hjelpemidler.serialization.jackson.value
import java.time.LocalDate

abstract class NyOrdrelinje {
    protected val JsonMessage.eventId get() = uuidValue("eventId")
    protected val JsonMessage.opprettet get() = this["opprettet"].stringValue()
    protected val JsonMessage.fnrBruker get() = this["fnrBruker"].stringValue()
    protected val JsonMessage.data get() = this["data"].value<Map<String, Any?>>()
    protected val JsonMessage.innkommendeOrdrelinje get() = this["data"].value<InnkommendeOrdrelinje>()
    protected val JsonMessage.sistOppdatert: LocalDate?
        get() = this["data"].path("sistOppdatert").stringValueOrNull()?.let(LocalDate::parse)

    /**
     * Ordrelinjer som har blitt forsinket på vei til oss (f.eks. ved reprosessering eller etterslep i OeBS) lagres,
     * men vi varsler ikke bruker om dem hvis siste endring i OeBS er eldre enn syv dager. Mangler datoen varsler vi
     * som før.
     */
    protected fun skalVarsleOmOrdrelinje(sistOppdatert: LocalDate?): Boolean =
        sistOppdatert == null || sistOppdatert >= LocalDate.now().minusDays(7)

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
         * Transformer ordrelinje fra OeBS til ordrelinje for lagring i hm-soknadsbehandling-db
         */
        fun tilOrdrelinje(søknadId: BehovsmeldingId, fnrBruker: String, data: Map<String, Any?>): Ordrelinje =
            Ordrelinje(
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
