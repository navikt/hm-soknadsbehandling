package no.nav.hjelpemidler.soknad.mottak.river

import com.fasterxml.jackson.databind.node.NullNode
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.hjelpemidler.behovsmeldingsmodell.BehovsmeldingStatus
import no.nav.hjelpemidler.behovsmeldingsmodell.BehovsmeldingType
import no.nav.hjelpemidler.behovsmeldingsmodell.ordre.Ordrelinje
import no.nav.hjelpemidler.behovsmeldingsmodell.sak.HotsakSakId
import no.nav.hjelpemidler.soknad.mottak.client.HarOrdre
import no.nav.hjelpemidler.soknad.mottak.client.Søknad
import no.nav.hjelpemidler.soknad.mottak.client.SøknadsbehandlingClient
import no.nav.hjelpemidler.soknad.mottak.test.Testdata
import java.time.Instant
import java.util.UUID
import kotlin.test.Test

class NyHotsakOrdrelinjeTest {
    private val mock = mockk<SøknadsbehandlingClient>()
    private val rapid = TestRapid().apply {
        NyHotsakOrdrelinje(this, mock)
    }

    @Test
    fun `Behandler ny ordrelinje`() {
        val message = Testdata.testmeldingerFraOebs.first()
        val søknadId = UUID.randomUUID()
        val sakId = message.at("/data/saksnummer").textValue().let(::HotsakSakId)

        coEvery {
            mock.finnSøknadForSak(sakId)
        } returns Søknad(
            søknadId = søknadId,
            søknadOpprettet = Instant.now(),
            søknadEndret = Instant.now(),
            søknadGjelder = "",
            fnrInnsender = null,
            fnrBruker = "",
            navnBruker = "",
            kommunenavn = null,
            journalpostId = null,
            oppgaveId = null,
            digital = true,
            behovsmeldingstype = BehovsmeldingType.SØKNAD,
            status = BehovsmeldingStatus.GODKJENT,
            statusEndret = Instant.now(),
            data = NullNode.getInstance(),
        )

        coEvery {
            mock.behovsmeldingTypeFor(søknadId)
        } returns BehovsmeldingType.SØKNAD

        coEvery {
            mock.ordreSisteDøgn(søknadId)
        } returns HarOrdre(harOrdreAvTypeHjelpemidler = false, harOrdreAvTypeDel = false)

        coEvery {
            mock.lagreOrdrelinje(any<Ordrelinje>())
        } returns 1

        coEvery {
            mock.oppdaterStatus(søknadId, BehovsmeldingStatus.UTSENDING_STARTET)
        } returns 1

        rapid.sendTestMessage(message.toString())

        val inspektør = rapid.inspektør

        inspektør.key(0) shouldBe message.at("/fnrBruker").textValue()

        val message1 = inspektør.message(0)
        message1.at("/eventName").textValue() shouldBe "hm-OrdrelinjeMottatt"
        message1.at("/saksnummer").textValue() shouldBe sakId.value
    }
}
