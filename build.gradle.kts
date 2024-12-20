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
    implementation(libs.kotlin.logging)
    implementation(libs.bundles.jackson)
    implementation(libs.rapidsAndRivers)
    implementation(libs.influxdb.client.kotlin)
    runtimeOnly(libs.bundles.logging.runtime)

    // DigiHoT
    implementation(libs.hm.behovsmeldingsmodell)
    implementation(libs.hotlibs.http) {
        exclude("io.ktor", "ktor-client-cio")
    }

    // Ktor
    implementation(libs.ktor.client.apache)

    // GraphQL Client
    implementation(libs.graphql.ktor.client) {
        exclude("com.expediagroup", "graphql-kotlin-client-serialization") // prefer jackson
        exclude("io.ktor", "ktor-client-serialization") // prefer ktor-client-jackson
        exclude("io.ktor", "ktor-client-cio") // prefer ktor-client-apache
    }
    implementation(libs.graphql.client.jackson)
}

java { toolchain { languageVersion.set(JavaLanguageVersion.of(21)) } }

@Suppress("UnstableApiUsage")
testing {
    suites {
        val test by getting(JvmTestSuite::class) {
            useKotlinTest(libs.versions.kotlin.asProvider())
            dependencies {
                implementation(libs.handlebars)
                implementation(libs.jackson.dataformat.yaml)
                implementation(libs.kotest.assertions.core)
                implementation(libs.ktor.server.test.host)
                implementation(libs.mockk)
                implementation(libs.tbdLibs.rapidsAndRivers.test)
            }
            targets.configureEach {
                testTask {
                    testLogging {
                        events = setOf(TestLogEvent.SKIPPED, TestLogEvent.FAILED)
                    }
                }
            }
        }
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

tasks.shadowJar { mergeServiceFiles() }
