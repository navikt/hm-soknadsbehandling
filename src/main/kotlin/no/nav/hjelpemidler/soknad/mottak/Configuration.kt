package no.nav.hjelpemidler.soknad.mottak

import com.natpryce.konfig.*
import com.natpryce.konfig.ConfigurationProperties.Companion.systemProperties
import java.io.File
import java.net.InetAddress
import java.net.UnknownHostException

private val localProperties = ConfigurationMap(
    mapOf(
        "application.httpPort" to "8080",
        "application.profile" to "LOCAL",
        "database.host" to "localhost",
        "database.name" to "dp-soknad",
        "database.password" to "postgres",
        "database.port" to "5432",
        "database.user" to "postgres",
        "kafka.bootstrap.servers" to "localhost:9092",
        "kafka.extra.topic" to "privat-dagpenger-journalpost-mottatt-v1, privat-dagpenger-soknadsdata-v1",
        "kafka.reset.policy" to "earliest",
        "kafka.topic" to "privat-dagpenger-behov-v2",
        "nav.truststore.password" to "foo",
        "nav.truststore.path" to "bla/bla",
        "pdl.baseurl" to "http://localhost:8000",
        "sts.baseurl" to "http://localhost:7000",
        "sts.password" to "password",
        "sts.user" to "user",
        "vault.mountpath" to "postgresql/dev/",
    )
)
private val devProperties = ConfigurationMap(
    mapOf(
        "application.httpPort" to "8080",
        "application.profile" to "DEV",
        "database.host" to "b27dbvl013.preprod.local",
        "database.name" to "dp-soknad",
        "database.port" to "5432",
        "kafka.bootstrap.servers" to "b27apvl00045.preprod.local:8443,b27apvl00046.preprod.local:8443,b27apvl00047.preprod.local:8443",
        "kafka.extra.topic" to "privat-dagpenger-journalpost-mottatt-v1, privat-dagpenger-soknadsdata-v1",
        "kafka.reset.policy" to "earliest",
        "kafka.topic" to "privat-dagpenger-behov-v2",
        "pdl.baseurl" to "http://pdl-api.default.svc.nais.local",
        "sts.baseurl" to "http://security-token-service.default.svc.nais.local",
        "vault.mountpath" to "postgresql/preprod-fss/",
    )
)
private val prodProperties = ConfigurationMap(
    mapOf(
        "application.httpPort" to "8080",
        "application.profile" to "PROD",
        "database.host" to "A01DBVL013.adeo.no",
        "database.name" to "dp-soknad",
        "database.port" to "5432",
        "kafka.bootstrap.servers" to "a01apvl00145.adeo.no:8443,a01apvl00146.adeo.no:8443,a01apvl00147.adeo.no:8443,a01apvl00148.adeo.no:8443,a01apvl00149.adeo.no:8443,a01apvl00150.adeo.no:8443",
        "kafka.extra.topic" to "privat-dagpenger-journalpost-mottatt-v1, privat-dagpenger-soknadsdata-v1",
        "kafka.reset.policy" to "earliest",
        "kafka.topic" to "privat-dagpenger-behov-v2",
        "pdl.baseurl" to "http://pdl-api.default.svc.nais.local",
        "sts.baseurl" to "http://security-token-service.default.svc.nais.local",
        "vault.mountpath" to "postgresql/prod-fss/",
    )
)

private fun config() = when (System.getenv("NAIS_CLUSTER_NAME") ?: System.getProperty("NAIS_CLUSTER_NAME")) {
    "dev-fss" -> systemProperties() overriding EnvironmentVariables overriding devProperties
    "prod-fss" -> systemProperties() overriding EnvironmentVariables overriding prodProperties
    else -> {
        systemProperties() overriding EnvironmentVariables overriding localProperties
    }
}

internal object Configuration {
    val database: Database = Database()
    val vault: Vault = Vault()
    val application: Application = Application()
    val pdl: PDL = PDL()
    val sts: Sts = Sts()
    val rapidApplication: Map<String, String> = mapOf(
        "RAPID_APP_NAME" to "hm-soknadsbehandling",
        "KAFKA_BOOTSTRAP_SERVERS" to config()[Key("kafka.bootstrap.servers", stringType)],
        "KAFKA_CONSUMER_GROUP_ID" to application.id,
        "KAFKA_RAPID_TOPIC" to config()[Key("kafka.topic", stringType)],
        "KAFKA_EXTRA_TOPIC" to config()[Key("kafka.extra.topic", stringType)],
        "KAFKA_RESET_POLICY" to config()[Key("kafka.reset.policy", stringType)],
        "NAV_TRUSTSTORE_PATH" to config()[Key("nav.truststore.path", stringType)],
        "NAV_TRUSTSTORE_PASSWORD" to config()[Key("nav.truststore.password", stringType)]
    ) + System.getenv().filter { it.key.startsWith("NAIS_") }

    data class PDL(
        val baseUrl: String = config()[Key("pdl.baseurl", stringType)]
    )

    data class Sts(
        val baseUrl: String = config()[Key("sts.baseurl", stringType)],
        val user: String = config().getOrElse(Key("sts.user", stringType)) {
            "/var/run/secrets/nais.io/service_user/username".readFile()
        },
        val password: String = config().getOrElse(Key("sts.password", stringType)) {
            "/var/run/secrets/nais.io/service_user/password".readFile()
        }
    )

    data class Database(
        val host: String = config()[Key("database.host", stringType)],
        val port: String = config()[Key("database.port", stringType)],
        val name: String = config()[Key("database.name", stringType)],
        val user: String? = config().getOrNull(Key("database.user", stringType)),
        val password: String? = config().getOrNull(Key("database.password", stringType))
    )

    data class Vault(
        val mountPath: String = config()[Key("vault.mountpath", stringType)]
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
