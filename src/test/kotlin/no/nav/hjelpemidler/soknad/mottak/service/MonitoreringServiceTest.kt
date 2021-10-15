package no.nav.hjelpemidler.soknad.mottak.service

import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import mu.KLogger
import no.nav.hjelpemidler.soknad.mottak.client.SøknadForRiverClient
import org.junit.jupiter.api.Test

internal class MonitoreringServiceTest {

    private val errorMsg = slot<() -> String>()
    private val soknadForRiverClient = mockk<SøknadForRiverClient>()
    private val logger = mockk<KLogger>(relaxed = true).apply {
        every { error(capture(errorMsg)) }
    }

    @Test
    fun `Test rapporterGodkjenteSøknaderUtenOppgave skal logge error når søknader mangler oppgave`() = runBlocking {

        soknadForRiverClient.apply {
            coEvery { hentGodkjenteSøknaderUtenOppgaveEldreEnn(any()) } returns listOf("7832gh-h7y283h23nsm0983", "1000-liters-of-milk")
        }

        val monitoreringService = MonitoreringService(soknadForRiverClient, logger = logger)
        monitoreringService.rapporterGodkjenteSøknaderUtenOppgave()

        errorMsg.captured.invoke().substring(0, 44) shouldBe "Det finnes 2 godkjente søknader uten oppgave"
    }

    @Test
    fun `Test rapporterGodkjenteSøknaderUtenOppgave skal ikke logge error når ingen søknader mangler oppgave`() = runBlocking {

        soknadForRiverClient.apply {
            coEvery { hentGodkjenteSøknaderUtenOppgaveEldreEnn(any()) } returns emptyList()
        }

        val monitoreringService = MonitoreringService(soknadForRiverClient, logger = logger)
        monitoreringService.rapporterGodkjenteSøknaderUtenOppgave()

        verify(exactly = 0) { logger.error { errorMsg } }
    }
}
