package no.nav.helse.rapids_rivers

import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.config.SslConfigs
import org.apache.kafka.common.security.auth.SecurityProtocol
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.Properties

// Understands how to configure kafka from environment variables
class KafkaConfig(
    private val bootstrapServers: String,
    private val consumerGroupId: String,
    private val clientId: String? = null,
    private val sslTruststoreLocationEnvKey: String? = null,
    private val sslTruststorePasswordEnvKey: String? = null,
    private val javaKeystore: String? = "jks",
    private val pkcs12: String? = "PKCS12",
    private val sslKeystoreLocationEnvKey: String? = null,
    private val sslKeystorePasswordEnvKey: String? = null,
    private val autoOffsetResetConfig: String? = null,
    private val autoCommit: Boolean? = false,
    maxIntervalMs: Int? = null,
    maxRecords: Int? = null
) {
    private companion object {
        private const val DefaultMaxRecords = 200
    }

    private val maxPollRecords = maxRecords ?: DefaultMaxRecords

    // assuming a "worst case" scenario where it takes 2 seconds to process each message;
    // then set MAX_POLL_INTERVAL_MS_CONFIG 1 minute above this "worst case" limit so
    // the broker doesn't think we have died (and revokes partitions)
    private val maxPollIntervalMs = maxIntervalMs ?: Duration.ofSeconds(60 + maxPollRecords * 2.toLong()).toMillis()

    private val log = LoggerFactory.getLogger(this::class.java)

    internal fun consumerConfig() = Properties().apply {
        putAll(kafkaBaseConfig())
        put(ConsumerConfig.GROUP_ID_CONFIG, consumerGroupId)
        clientId?.also { put(ConsumerConfig.CLIENT_ID_CONFIG, "consumer-$it") }
        put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, autoOffsetResetConfig ?: "latest")
        put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, if (true == autoCommit) "true" else "false")
        put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "$maxPollRecords")
        put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, "$maxPollIntervalMs")
    }

    internal fun producerConfig() = Properties().apply {
        putAll(kafkaBaseConfig())
        clientId?.also { put(ProducerConfig.CLIENT_ID_CONFIG, "producer-$it") }
        put(ProducerConfig.ACKS_CONFIG, "1")
        put(ProducerConfig.LINGER_MS_CONFIG, "0")
        put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, "1")
    }

    private fun kafkaBaseConfig() = Properties().apply {
        put(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers)
        put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, SecurityProtocol.SSL.name)
        put(SslConfigs.SSL_ENDPOINT_IDENTIFICATION_ALGORITHM_CONFIG, "")
        put(SslConfigs.SSL_TRUSTSTORE_TYPE_CONFIG, javaKeystore)
        put(SslConfigs.SSL_KEYSTORE_TYPE_CONFIG, pkcs12)
        put(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG, sslTruststoreLocationEnvKey)
        put(SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG, sslTruststorePasswordEnvKey)
        put(SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG, sslKeystoreLocationEnvKey)
        put(SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG, sslKeystorePasswordEnvKey)
    }
}