package no.nav.hjelpemidler.soknad.mottak.river

import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.hjelpemidler.soknad.mottak.client.SøknadForRiverClient
import no.nav.hjelpemidler.soknad.mottak.service.BehovsmeldingType
import no.nav.hjelpemidler.soknad.mottak.service.HarOrdre
import no.nav.hjelpemidler.soknad.mottak.service.OrdrelinjeData
import no.nav.hjelpemidler.soknad.mottak.service.Status
import no.nav.hjelpemidler.soknad.mottak.test.Testdata
import java.util.UUID
import kotlin.test.Test

internal class NyHotsakOrdrelinjeTest {
    private val client = mockk<SøknadForRiverClient>()
    private val rapid = TestRapid().apply {
        NyHotsakOrdrelinje(this, client)
    }

    @Test
    internal fun `behandler ny ordrelinje`() {
        val message = Testdata.testmeldingerFraOebs.first()
        val søknadId = UUID.randomUUID()
        val saksnummer = message.at("/data/saksnummer").textValue()

        coEvery {
            client.hentSøknadIdFraHotsakSaksnummer(saksnummer)
        } returns søknadId

        coEvery {
            client.behovsmeldingTypeFor(søknadId)
        } returns BehovsmeldingType.SØKNAD

        coEvery {
            client.ordreSisteDøgn(søknadId)
        } returns HarOrdre(harOrdreAvTypeHjelpemidler = false, harOrdreAvTypeDel = false)

        coEvery {
            client.lagreSøknad(any<OrdrelinjeData>())
        } returns 1

        coEvery {
            client.oppdaterStatus(søknadId, Status.UTSENDING_STARTET)
        } returns 1

        rapid.sendTestMessage(message.toString())

        val inspektør = rapid.inspektør

        inspektør.key(0) shouldBe message.at("/fnrBruker").textValue()

        val message1 = inspektør.message(0)
        message1.at("/eventName").textValue() shouldBe "hm-OrdrelinjeMottatt"
        message1.at("/saksnummer").textValue() shouldBe saksnummer
    }
}
