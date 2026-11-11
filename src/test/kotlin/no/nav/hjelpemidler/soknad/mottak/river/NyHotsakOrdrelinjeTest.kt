package no.nav.hjelpemidler.soknad.mottak.river

import tools.jackson.databind.JsonNode
import tools.jackson.databind.node.ObjectNode
import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import no.nav.hjelpemidler.behovsmeldingsmodell.BehovsmeldingStatus
import no.nav.hjelpemidler.behovsmeldingsmodell.BehovsmeldingType
import no.nav.hjelpemidler.behovsmeldingsmodell.Statusendring
import no.nav.hjelpemidler.behovsmeldingsmodell.SøknadDto
import no.nav.hjelpemidler.behovsmeldingsmodell.ordre.Ordrelinje
import no.nav.hjelpemidler.behovsmeldingsmodell.sak.HotsakSakId
import no.nav.hjelpemidler.soknad.mottak.client.HarOrdre
import no.nav.hjelpemidler.soknad.mottak.client.SøknadsbehandlingClient
import no.nav.hjelpemidler.soknad.mottak.soknadsbehandling.SøknadsbehandlingService
import no.nav.hjelpemidler.soknad.mottak.test.Testdata
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlin.test.Test

class NyHotsakOrdrelinjeTest {
    private val mock = mockk<SøknadsbehandlingClient>()
    private val rapid = TestRapid().apply {
        NyHotsakOrdrelinje(this, SøknadsbehandlingService(mock))
    }

    @Test
    fun `Behandler ny ordrelinje`() {
        val message = Testdata.testmeldingerFraOebs.first()
        val sakId = message.at("/data/saksnummer").stringValue().let(::HotsakSakId)
        mockSøknadForSak(sakId)

        rapid.sendTestMessage(message.toString())

        val inspektør = rapid.inspektør

        inspektør.key(0) shouldBe message.at("/fnrBruker").stringValue()

        val message1 = inspektør.message(0)
        message1.at("/eventName").stringValue() shouldBe "hm-OrdrelinjeMottatt"
        message1.at("/saksnummer").stringValue() shouldBe sakId.value
    }

    @Test
    fun `Varsler om ordrelinje som er oppdatert nylig`() {
        val message = meldingMedSistOppdatert(LocalDate.now().minusDays(7))
        val søknadId = mockSøknadForSak(message.at("/data/saksnummer").stringValue().let(::HotsakSakId))

        rapid.sendTestMessage(message.toString())

        rapid.inspektør.size shouldBe 2
        rapid.inspektør.message(1).at("/eventName").stringValue() shouldBe "hm-OrdrelinjeLagret"
        coVerify { mock.lagreOrdrelinje(any<Ordrelinje>()) }
        coVerify { mock.oppdaterStatus(søknadId, any<Statusendring>()) }
    }

    @Test
    fun `Lagrer, men varsler ikke om ordrelinje med utdatert sistOppdatert`() {
        val message = meldingMedSistOppdatert(LocalDate.now().minusDays(8))
        val søknadId = mockSøknadForSak(message.at("/data/saksnummer").stringValue().let(::HotsakSakId))

        rapid.sendTestMessage(message.toString())

        rapid.inspektør.size shouldBe 1
        rapid.inspektør.message(0).at("/eventName").stringValue() shouldBe "hm-OrdrelinjeMottatt"
        coVerify { mock.lagreOrdrelinje(any<Ordrelinje>()) }
        coVerify { mock.oppdaterStatus(søknadId, any<Statusendring>()) }
    }

    @Test
    fun `Varsler om ordrelinje uten sistOppdatert`() {
        val message = Testdata.testmeldingerFraOebs.first().deepCopy().apply {
            (get("data") as ObjectNode).remove("sistOppdatert")
        }
        mockSøknadForSak(message.at("/data/saksnummer").stringValue().let(::HotsakSakId))

        rapid.sendTestMessage(message.toString())

        rapid.inspektør.size shouldBe 2
        rapid.inspektør.message(1).at("/eventName").stringValue() shouldBe "hm-OrdrelinjeLagret"
    }

    private fun meldingMedSistOppdatert(sistOppdatert: LocalDate): JsonNode =
        Testdata.testmeldingerFraOebs.first().deepCopy().apply {
            (get("data") as ObjectNode).put("sistOppdatert", sistOppdatert.toString())
        }

    private fun mockSøknadForSak(sakId: HotsakSakId): UUID {
        val søknadId = UUID.randomUUID()

        coEvery {
            mock.finnSøknadForSak(sakId)
        } returns SøknadDto(
            søknadId = søknadId,
            søknadOpprettet = Instant.now(),
            søknadEndret = Instant.now(),
            søknadGjelder = "",
            fnrInnsender = null,
            fnrBruker = "",
            navnBruker = "",
            journalpostId = null,
            oppgaveId = null,
            digital = true,
            behovsmeldingstype = BehovsmeldingType.SØKNAD,
            status = BehovsmeldingStatus.GODKJENT,
            statusEndret = Instant.now(),
        )

        coEvery {
            mock.ordreSisteDøgn(søknadId)
        } returns HarOrdre(harOrdreAvTypeHjelpemidler = false, harOrdreAvTypeDel = false)

        coEvery {
            mock.lagreOrdrelinje(any<Ordrelinje>())
        } returns 1

        coEvery {
            mock.oppdaterStatus(søknadId, Statusendring(BehovsmeldingStatus.UTSENDING_STARTET, null, null))
        } returns 1

        return søknadId
    }
}
