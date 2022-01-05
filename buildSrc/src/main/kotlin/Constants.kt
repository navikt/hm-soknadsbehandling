/***
 *  Avhengigheter for Dapgenger jvm prosjekter.
 *
 *  Denne fila skal kun editeres i fra https://github.com/navikt/dp-service-template. Sjekk inn ny versjon og kjør
 *  meta sync
 *
 */

object Avro {
    const val avro = "org.apache.avro:avro:1.9.2"
}

object Bekk {
    const val nocommons = "no.bekk.bekkopen:nocommons:0.9.0"
}

object Cucumber {
    const val version = "4.8.0"
    const val java8 = "io.cucumber:cucumber-java8:$version"
    const val junit = "io.cucumber:cucumber-junit:$version"
    fun library(name: String) = "io.cucumber:cucumber-$name:$version"
}

object Dagpenger {

    object Biblioteker {
        const val version = "2020.09.16-07.28.a797a00c180a"
        const val stsKlient = "com.github.navikt.dp-biblioteker:sts-klient:$version"
        const val grunnbeløp = "com.github.navikt.dp-biblioteker:grunnbelop:$version"
        const val ktorUtils = "com.github.navikt.dp-biblioteker:ktor-utils:$version"

        object Ktor {
            object Server {
                const val apiKeyAuth = "com.github.navikt.dp-biblioteker:ktor-utils:$version"
            }

            object Client {
                const val metrics = "com.github.navikt.dp-biblioteker:ktor-client-metrics:$version"
                const val authBearer = "com.github.navikt.dp-biblioteker:ktor-client-auth-bearer:$version"
            }
        }

        object Soap {
            const val client = "com.github.navikt.dp-biblioteker:soap-client:$version"
        }
    }

    const val Streams = "com.github.navikt:dagpenger-streams:2020.08.19-13.32.0fd360f3ef11"
    const val Events = "com.github.navikt:dagpenger-events:2020.08.19-10.57.d2fe892352eb"
}

object Database {
    const val Postgres = "org.postgresql:postgresql:42.3.1"
    const val Kotlinquery = "com.github.seratch:kotliquery:1.3.1"
    const val Flyway = "org.flywaydb:flyway-core:6.3.2"
    const val HikariCP = "com.zaxxer:HikariCP:3.4.1"
    const val VaultJdbc = "no.nav:vault-jdbc:1.3.9"
}

object Fuel {
    const val version = "2.2.1"
    const val fuel = "com.github.kittinunf.fuel:fuel:$version"
    fun library(name: String) = "com.github.kittinunf.fuel:fuel-$name:$version"
}

object Jackson {
    const val version = "2.13.1"
    const val core = "com.fasterxml.jackson.core:jackson-core:$version"
    const val kotlin = "com.fasterxml.jackson.module:jackson-module-kotlin:$version"
    const val jsr310 = "com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$version"
}

object Junit5 {
    const val version = "5.6.1"
    const val api = "org.junit.jupiter:junit-jupiter-api:$version"
    const val params = "org.junit.jupiter:junit-jupiter-params:$version"
    const val engine = "org.junit.jupiter:junit-jupiter-engine:$version"
    const val vintageEngine = "org.junit.vintage:junit-vintage-engine:$version"
    fun library(name: String) = "org.junit.jupiter:junit-jupiter-$name:$version"
}

object Json {
    const val version = "20180813"
    const val library = "org.json:json:$version"
}

object JsonAssert {
    const val version = "1.5.0"
    const val jsonassert = "org.skyscreamer:jsonassert:$version"
}

object Kafka {
    const val version = "2.3.1"
    const val clients = "org.apache.kafka:kafka-clients:$version"
    const val streams = "org.apache.kafka:kafka-streams:$version"
    const val streamTestUtils = "org.apache.kafka:kafka-streams-test-utils:$version"
    fun library(name: String) = "org.apache.kafka:kafka-$name:$version"

    object Confluent {
        const val version = "5.3.0"
        const val avroStreamSerdes = "io.confluent:kafka-streams-avro-serde:$version"
        fun library(name: String) = "io.confluent:$name:$version"
    }
}

object KafkaEmbedded {
    const val env = "no.nav:kafka-embedded-env:2.2.3"
}

object Klint {
    const val version = "0.33.0"
}

object Konfig {
    const val konfig = "com.natpryce:konfig:1.6.10.0"
}

object Kotlin {
    const val version = "1.5.31"
    const val stdlib = "org.jetbrains.kotlin:kotlin-stdlib:$version"

    const val testJUnit5 = "org.jetbrains.kotlin:kotlin-test-junit5:$version"

    object Logging {
        const val version = "1.7.9"
        const val kotlinLogging = "io.github.microutils:kotlin-logging:$version"
    }
}

object KoTest {
    const val version = "5.0.3"

    // for kotest framework
    const val runner = "io.kotest:kotest-runner-junit5-jvm:$version"

    // for kotest core jvm assertion
    const val assertions = "io.kotest:kotest-assertions-core-jvm:$version"

    // for kotest property test
    const val property = "io.kotest:kotest-property-jvm:$version"

    // any other library
    fun library(name: String) = "io.kotest:kotest-$name:$version"
}

object Ktor {
    const val version = "1.6.7"
    const val server = "io.ktor:ktor-server:$version"
    const val serverNetty = "io.ktor:ktor-server-netty:$version"
    const val auth = "io.ktor:ktor-auth:$version"
    const val authJwt = "io.ktor:ktor-auth-jwt:$version"
    const val locations = "io.ktor:ktor-locations:$version"
    const val micrometerMetrics = "io.ktor:ktor-metrics-micrometer:$version"
    const val ktorTest = "io.ktor:ktor-server-test-host:$version"
    fun library(name: String) = "io.ktor:ktor-$name:$version"
}

object Micrometer {
    const val version = "1.4.0"
    const val prometheusRegistry = "io.micrometer:micrometer-registry-prometheus:$version"
}

object Moshi {
    const val version = "1.9.2"
    const val moshi = "com.squareup.moshi:moshi:$version"
    const val moshiKotlin = "com.squareup.moshi:moshi-kotlin:$version"
    const val moshiAdapters = "com.squareup.moshi:moshi-adapters:$version"

    // waiting for https://github.com/rharter/ktor-moshi/pull/8
    const val moshiKtor = "com.github.cs125-illinois:ktor-moshi:7252ca49ed"
    fun library(name: String) = "com.squareup.moshi:moshi-$name:$version"
}

object Mockk {
    const val version = "1.10.0"
    const val mockk = "io.mockk:mockk:$version"
}

object Nare {
    const val version = "768ae37"
    const val nare = "no.nav:nare:$version"
}

const val RapidAndRivers = "com.github.navikt:rapids-and-rivers:1.6d6256d"

object Ktlint {
    const val version = "0.38.1"
}

object Spotless {
    const val version = "5.1.0"
    const val spotless = "com.diffplug.spotless"
}

object Shadow {
    const val version = "5.2.0"
    const val shadow = "com.github.johnrengelman.shadow"
}

object TestContainers {
    const val version = "1.16.2"
    const val postgresql = "org.testcontainers:postgresql:$version"
    const val kafka = "org.testcontainers:kafka:$version"
}

object Ulid {
    const val version = "8.2.0"
    const val ulid = "de.huxhorn.sulky:de.huxhorn.sulky.ulid:$version"
}

object Wiremock {
    const val version = "2.32.0"
    const val standalone = "com.github.tomakehurst:wiremock-jre8-standalone:$version"
}

object GraphQL {
    const val version = "5.2.0"
    const val graphql = "com.expediagroup.graphql"
    val ktorClient = library("ktor-client")
    val clientJackson = library("client-jackson")
    fun library(name: String) = "com.expediagroup:graphql-kotlin-$name:$version"
}
