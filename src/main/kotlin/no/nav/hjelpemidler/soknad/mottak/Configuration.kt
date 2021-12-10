package no.nav.hjelpemidler.soknad.mottak

import com.natpryce.konfig.ConfigurationMap
import com.natpryce.konfig.ConfigurationProperties.Companion.systemProperties
import com.natpryce.konfig.EnvironmentVariables
import com.natpryce.konfig.Key
import com.natpryce.konfig.intType
import com.natpryce.konfig.overriding
import com.natpryce.konfig.stringType

private val localProperties = ConfigurationMap(

    mapOf(
        "application.httpPort" to "8082",
        "application.profile" to "LOCAL",
        "KAFKA_RESET_POLICY" to "earliest",
        "KAFKA_TOPIC" to "teamdigihot.hm-soknadsbehandling-v1",
        "KAFKA_TRUSTSTORE_PATH" to "",
        "KAFKA_CREDSTORE_PASSWORD" to "",
        "KAFKA_KEYSTORE_PATH" to "",
        "kafka.truststore.password" to "foo",
        "KAFKA_BROKERS" to "host.docker.internal:9092",
        "AZURE_TENANT_BASEURL" to "http://localhost:9098",
        "AZURE_APP_TENANT_ID" to "123",
        "AZURE_APP_CLIENT_ID" to "123",
        "AZURE_APP_CLIENT_SECRET" to "dummy",
        "DBAPI_SCOPE" to "123",
        "INFLUX_HOST" to "http://localhost",
        "INFLUX_PORT" to "1234",
        "INFLUX_DATABASE_NAME" to "defaultdb",
        "INFLUX_USER" to "user",
        "INFLUX_PASSWORD" to "password",
        "SOKNADSBEHANDLING_DB_BASEURL" to "http://localhost:8083/api",
        "SOKNADSBEHANDLING_DB_CLIENT_ID" to "local:hm-soknadsbehandling-db",
        "PDL_API_URL" to "http://localhost:9098/pdl",
        "PDL_API_SCOPE" to "api://dev-gcp.pdl.pdl-api/.default",
    )
)

private val devProperties = ConfigurationMap(
    mapOf(
        "application.httpPort" to "8080",
        "application.profile" to "DEV",
        "KAFKA_RESET_POLICY" to "earliest",
        "KAFKA_TOPIC" to "teamdigihot.hm-soknadsbehandling-v1",
        "AZURE_TENANT_BASEURL" to "https://login.microsoftonline.com",
        "DBAPI_SCOPE" to "api://dev-gcp.teamdigihot.hm-soknadsbehandling-db/.default",
        "SOKNADSBEHANDLING_DB_CLIENT_ID" to "dev-gcp:teamdigihot:hm-soknadsbehandling-db",
        "PDL_API_URL" to "https://pdl-api.dev-fss-pub.nais.io/graphql",
        "PDL_API_SCOPE" to "api://dev-fss.pdl.pdl-api/.default",
    )
)
private val prodProperties = ConfigurationMap(
    mapOf(
        "application.httpPort" to "8080",
        "application.profile" to "PROD",
        "KAFKA_RESET_POLICY" to "earliest",
        "KAFKA_TOPIC" to "teamdigihot.hm-soknadsbehandling-v1",
        "AZURE_TENANT_BASEURL" to "https://login.microsoftonline.com",
        "DBAPI_SCOPE" to "api://prod-gcp.teamdigihot.hm-soknadsbehandling-db/.default",
        "SOKNADSBEHANDLING_DB_CLIENT_ID" to "prod-gcp:teamdigihot:hm-soknadsbehandling-db",
        "PDL_API_URL" to "https://pdl-api.prod-fss-pub.nais.io/graphql",
        "PDL_API_SCOPE" to "api://prod-fss.pdl.pdl-api/.default",
    )
)

internal object Configuration {
    val appName = System.getenv("NAIS_APP_NAME") ?: "hm-soknadsbehandling"
    val cluster = System.getenv("NAIS_CLUSTER_NAME") ?: System.getProperty("NAIS_CLUSTER_NAME") ?: "LOCAL"

    private val config = when (cluster) {
        "dev-gcp" -> systemProperties() overriding EnvironmentVariables overriding devProperties
        "prod-gcp" -> systemProperties() overriding EnvironmentVariables overriding prodProperties
        else -> {
            systemProperties() overriding EnvironmentVariables overriding localProperties
        }
    }

    val soknadsbehandlingDb: SoknadsbehandlingDb = SoknadsbehandlingDb()
    val azure: Azure = Azure()
    val application: Application = Application()
    val influxDb: InfluxDb = InfluxDb()
    val pdl: Pdl = Pdl()
    val rapidApplication: Map<String, String> = mapOf(
        "RAPID_KAFKA_CLUSTER" to "gcp",
        "RAPID_APP_NAME" to "hm-soknadsbehandling",
        "KAFKA_BROKERS" to config[Key("KAFKA_BROKERS", stringType)],
        "KAFKA_CONSUMER_GROUP_ID" to application.id,
        "KAFKA_RAPID_TOPIC" to config[Key("KAFKA_TOPIC", stringType)],
        "KAFKA_RESET_POLICY" to config[Key("KAFKA_RESET_POLICY", stringType)],
        "KAFKA_TRUSTSTORE_PATH" to config[Key("KAFKA_TRUSTSTORE_PATH", stringType)],
        "KAFKA_CREDSTORE_PASSWORD" to config[Key("KAFKA_CREDSTORE_PASSWORD", stringType)],
        "KAFKA_KEYSTORE_PATH" to config[Key("KAFKA_KEYSTORE_PATH", stringType)],
        "HTTP_PORT" to config[Key("application.httpPort", stringType)],
    ) + System.getenv().filter { it.key.startsWith("NAIS_") }

    data class Application(
        val id: String = config.getOrElse(Key("", stringType), "hm-soknadsbehandling-v1"),
        val profile: Profile = config[Key("application.profile", stringType)].let { Profile.valueOf(it) },
        val httpPort: Int = config[Key("application.httpPort", intType)],
    )

    data class Azure(
        val tenantBaseUrl: String = config[Key("AZURE_TENANT_BASEURL", stringType)],
        val tenantId: String = config[Key("AZURE_APP_TENANT_ID", stringType)],
        val clientId: String = config[Key("AZURE_APP_CLIENT_ID", stringType)],
        val clientSecret: String = config[Key("AZURE_APP_CLIENT_SECRET", stringType)],
        val dbApiScope: String = config[Key("DBAPI_SCOPE", stringType)]
    )

    data class InfluxDb(
        val host: String = config[Key("INFLUX_HOST", stringType)],
        val port: String = config[Key("INFLUX_PORT", stringType)],
        val name: String = config[Key("INFLUX_DATABASE_NAME", stringType)],
        val user: String = config[Key("INFLUX_USER", stringType)],
        val password: String = config[Key("INFLUX_PASSWORD", stringType)]
    )

    data class Pdl(
        val baseUrl: String = config[Key("PDL_API_URL", stringType)],
        val apiScope: String = config[Key("PDL_API_SCOPE", stringType)]
    )

    data class SoknadsbehandlingDb(
        val baseUrl: String = config[Key("SOKNADSBEHANDLING_DB_BASEURL", stringType)],
    )
}

enum class Profile {
    LOCAL, DEV, PROD
}
