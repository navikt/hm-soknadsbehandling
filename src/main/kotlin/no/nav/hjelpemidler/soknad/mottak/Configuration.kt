package no.nav.hjelpemidler.soknad.mottak

import com.natpryce.konfig.ConfigurationMap
import com.natpryce.konfig.ConfigurationProperties.Companion.systemProperties
import com.natpryce.konfig.EnvironmentVariables
import com.natpryce.konfig.Key
import com.natpryce.konfig.intType
import com.natpryce.konfig.overriding
import com.natpryce.konfig.stringType
import java.io.File
import java.net.InetAddress
import java.net.UnknownHostException

private val localProperties = ConfigurationMap(
    mapOf(
        "application.httpPort" to "8080",
        "application.profile" to "LOCAL",
        "db.host" to "localhost",
        "db.database" to "soknadsbehandling",
        "db.password" to "postgres",
        "db.port" to "5432",
        "db.username" to "postgres",
        "kafka.extra.topic" to "hm-soknadsdata-v1",
        "kafka.reset.policy" to "earliest",
        "kafka.topic" to "hm-soknadsbehandling-v1",
        "kafka.truststore.password" to "foo",
        "kafka.truststore.path" to "bla/bla",
        "kafka.credstore.password" to "foo",
        "kafka.keystore.path" to "bla/bla",
        "kafka.brokers" to "localhost:9092",
    )
)
private val devProperties = ConfigurationMap(
    mapOf(
        "application.httpPort" to "8080",
        "application.profile" to "DEV",
        "kafka.extra.topic" to "hm-soknadsdata-v1",
        "kafka.reset.policy" to "earliest",
        "kafka.topic" to "hm-soknadsbehandling-v1",
    )
)
private val prodProperties = ConfigurationMap(
    mapOf(
        "application.httpPort" to "8080",
        "application.profile" to "PROD",
        "kafka.extra.topic" to "hm-soknadsdata-v1",
        "kafka.reset.policy" to "earliest",
        "kafka.topic" to "hm-soknadsbehandling-v1",
    )
)

private fun config() = when (System.getenv("NAIS_CLUSTER_NAME") ?: System.getProperty("NAIS_CLUSTER_NAME")) {
    "dev-gcp" -> systemProperties() overriding EnvironmentVariables overriding devProperties
    "prod-gcp" -> systemProperties() overriding EnvironmentVariables overriding prodProperties
    else -> {
        systemProperties() overriding EnvironmentVariables overriding localProperties
    }
}

internal object Configuration {
    val database: Database = Database()
    val application: Application = Application()
    val rapidApplication: Map<String, String> = mapOf(
        "RAPID_KAFKA_CLUSTER" to "gcp",
        "RAPID_APP_NAME" to "hm-soknadsbehandling",
        "KAFKA_BOOTSTRAP_SERVERS" to config()[Key("kafka.brokers", stringType)],
        "KAFKA_CONSUMER_GROUP_ID" to application.id,
        "KAFKA_RAPID_TOPIC" to config()[Key("kafka.topic", stringType)],
        "KAFKA_EXTRA_TOPIC" to config()[Key("kafka.extra.topic", stringType)],
        "KAFKA_RESET_POLICY" to config()[Key("kafka.reset.policy", stringType)],
        "KAFKA_TRUSTSTORE_PATH" to config()[Key("kafka.truststore.path", stringType)],
        "KAFKA_CREDSTORE_PASSWORD" to config()[Key("kafka.credstore.password", stringType)],
        "KAFKA_KEYSTORE_PATH" to config()[Key("kafka.keystore.path", stringType)],
        "KAFKA_KEYSTORE_PASSWORD" to config()[Key("kafka.credstore.password", stringType)],

    ) + System.getenv().filter { it.key.startsWith("NAIS_") }

    data class Database(
        val host: String = config()[Key("db.host", stringType)],
        val port: String = config()[Key("db.port", stringType)],
        val name: String = config()[Key("db.database", stringType)],
        val user: String? = config().getOrNull(Key("db.username", stringType)),
        val password: String? = config().getOrNull(Key("db.password", stringType))
    )

    data class Application(
        val id: String = config().getOrElse(Key("", stringType), "hm-soknadsbehandling-v2"),
        val profile: Profile = config()[Key("application.profile", stringType)].let { Profile.valueOf(it) },
        val httpPort: Int = config()[Key("application.httpPort", intType)]
    )
}

enum class Profile {
    LOCAL, DEV, PROD
}

private fun getHostname(): String {
    return try {
        val addr: InetAddress = InetAddress.getLocalHost()
        addr.hostName
    } catch (e: UnknownHostException) {
        "unknown"
    }
}

private fun String.readFile() =
    File(this).readText(Charsets.UTF_8)
