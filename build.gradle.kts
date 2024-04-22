
import com.expediagroup.graphql.plugin.gradle.tasks.GraphQLIntrospectSchemaTask
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent

val graphQLClientVersion = "6.5.6"

plugins {
    kotlin("jvm") version "1.9.23"
    id("com.expediagroup.graphql") version "6.5.6"
    id("io.ktor.plugin") version "2.3.10"
    id("com.diffplug.spotless") version "6.25.0"
}

group = "no.nav.hjelpemidler.soknad.mottak"

repositories {
    mavenCentral()
    maven("https://packages.confluent.io/maven/")
    maven("https://jitpack.io")
}

application {
    applicationName = "hm-soknadsbehandling"
    mainClass.set("no.nav.hjelpemidler.soknad.mottak.ApplicationKt")
}

fun ktor(name: String) = "io.ktor:ktor-$name"
fun graphqlKotlin(name: String) = "com.expediagroup:graphql-kotlin-$name:$graphQLClientVersion"

dependencies {
    implementation("com.github.navikt:rapids-and-rivers:2024020419561707073004.70bfb92c077c")

    // Logging
    runtimeOnly("ch.qos.logback:logback-classic:1.5.5")
    implementation("net.logstash.logback:logstash-logback-encoder:7.4")
    implementation("io.github.microutils:kotlin-logging:3.0.5")


    // Kotlin
    implementation(kotlin("stdlib"))
    implementation(kotlin("reflect"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.7.3") // følger ikke kotlin-versjon

    // Other
    implementation("com.natpryce:konfig:1.6.10.0")
    implementation("com.github.guepardoapps:kulid:2.0.0.0")

    // Jackson
    val jacksonVersion = "2.17.0"
    implementation("com.fasterxml.jackson.core:jackson-databind:$jacksonVersion")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jacksonVersion")

    // Ktor Client
    implementation(ktor("client-core"))
    implementation(ktor("client-apache"))
    implementation(ktor("serialization-jackson"))
    implementation(ktor("client-content-negotiation"))
    implementation(ktor("client-jackson"))

    // InfluxDB
    implementation("org.influxdb:influxdb-java:2.24")
    implementation("com.influxdb:influxdb-client-kotlin:7.0.0")

    // GraphQL Client
    implementation(graphqlKotlin("ktor-client")) {
        exclude("com.expediagroup", "graphql-kotlin-client-serialization") // prefer jackson
        exclude("io.ktor", "ktor-client-serialization") // prefer ktor-client-jackson
        exclude("io.ktor", "ktor-client-cio") // prefer ktor-client-apache
    }
    implementation(graphqlKotlin("client-jackson"))

    // Test
    testImplementation(kotlin("test"))
    testImplementation(ktor("server-test-host"))
    testImplementation("io.mockk:mockk:1.13.10")
    val kotestVersion = "5.8.1"
    testImplementation("io.kotest:kotest-assertions-core:$kotestVersion")
    testImplementation("io.kotest:kotest-runner-junit5:$kotestVersion")
}

kotlin { jvmToolchain { languageVersion.set(JavaLanguageVersion.of(21)) } }

tasks {
    test {
        useJUnitPlatform()
        testLogging {
            exceptionFormat = TestExceptionFormat.FULL
            events = setOf(TestLogEvent.PASSED, TestLogEvent.SKIPPED, TestLogEvent.FAILED)
        }
    }
    compileKotlin {
        dependsOn("spotlessApply")
        dependsOn("spotlessCheck")
    }
    named("buildFatJar") {
        dependsOn("test")
    }
}

graphql {
    client {
        schemaFile = file("src/main/resources/hmdb/schema.graphqls")
        queryFileDirectory = "src/main/resources/hmdb"
        packageName = "no.nav.hjelpemidler.soknad.mottak.client.hmdb"
    }
}

val graphqlIntrospectSchema by tasks.getting(GraphQLIntrospectSchemaTask::class) {
    endpoint.set("https://hm-grunndata-search.intern.dev.nav.no/graphql")
    outputFile.set(file("src/main/resources/hmdb/schema.graphqls"))
}
