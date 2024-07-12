import com.expediagroup.graphql.plugin.gradle.tasks.GraphQLIntrospectSchemaTask
import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktor)
    alias(libs.plugins.graphql)
    alias(libs.plugins.spotless)
}

group = "no.nav.hjelpemidler.soknad.mottak"

application {
    applicationName = "hm-soknadsbehandling"
    mainClass.set("no.nav.hjelpemidler.soknad.mottak.ApplicationKt")
}

dependencies {
    // Kotlin
    implementation(libs.kotlin.stdlib)

    // Rapids and Rivers
    implementation(libs.rapidsAndRivers)

    // Logging
    implementation(libs.kotlin.logging)
    runtimeOnly(libs.logback.classic)
    runtimeOnly(libs.logstash.logback.encoder)

    // Other
    // NB! Bytt til løsning fra hm-http
    implementation(libs.konfig.deprecated)
    // NB! Dette biblioteket vedlikeholdes ikke, hadde det holdt med UUID?
    implementation("com.github.guepardoapps:kulid:2.0.0.0")

    // Jackson
    implementation(libs.jackson.databind)
    implementation(libs.jackson.module.kotlin)
    implementation(libs.jackson.datatype.jsr310)

    // DigiHoT
    implementation(libs.hm.http) {
        exclude("io.ktor", "ktor-client-cio")
    }
    implementation(libs.ktor.client.apache)

    // InfluxDB
    implementation(libs.influxdb.client.kotlin)

    // GraphQL Client
    implementation(libs.graphql.ktor.client) {
        exclude("com.expediagroup", "graphql-kotlin-client-serialization") // prefer jackson
        exclude("io.ktor", "ktor-client-serialization") // prefer ktor-client-jackson
        exclude("io.ktor", "ktor-client-cio") // prefer ktor-client-apache
    }
    implementation(libs.graphql.client.jackson)

    // Test
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.mockk)
    testImplementation(libs.kotest.assertions.core)
}

java {
    toolchain {
        languageVersion.set(libs.versions.java.map(JavaLanguageVersion::of))
    }
}

tasks {
    test {
        useJUnitPlatform()
        testLogging {
            events = setOf(TestLogEvent.SKIPPED, TestLogEvent.FAILED)
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
