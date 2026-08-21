package no.nav.hjelpemidler.soknad.mottak.metrics

import tools.jackson.databind.SerializationFeature
import tools.jackson.module.kotlin.jacksonMapperBuilder
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.hjelpemidler.serialization.jackson.threeTenExtraModule
import tools.jackson.databind.cfg.DateTimeFeature
import java.time.LocalDateTime
import java.util.UUID

private val log = KotlinLogging.logger {}

class MetricsProducer(
    private val messageContext: MessageContext,
) {
    private val mapper = jacksonMapperBuilder()
        .addModule(threeTenExtraModule)
        .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
        .build()

    fun hendelseOpprettet(
        measurement: String,
        fields: Map<String, Any>,
        tags: Map<String, String>,
    ) {
        messageContext.publish(
            measurement,
            mapper.writeValueAsString(
                mapOf(
                    "eventId" to UUID.randomUUID(),
                    "eventName" to "hm-bigquery-sink-hendelse",
                    "schemaId" to "hendelse_v2",
                    "payload" to mapOf(
                        "opprettet" to LocalDateTime.now(),
                        "navn" to measurement,
                        "kilde" to "hm-soknadsbehandling",
                        "data" to fields.mapValues { it.value.toString() }
                            .plus(tags)
                            .filterKeys { it != "counter" }
                    )
                )
            )
        )
    }
}
