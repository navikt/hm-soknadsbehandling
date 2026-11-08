package no.nav.hjelpemidler.soknad.mottak.river

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import no.nav.hjelpemidler.behovsmeldingsmodell.sak.HotsakSakId
import no.nav.hjelpemidler.serialization.jackson.uuidValue
import java.util.UUID

fun JsonMessage.uuidValue(key: String): UUID = get(key).uuidValue()

fun JsonMessage.hotsakSakIdValue(key: String): HotsakSakId = HotsakSakId(get(key).stringValue())
