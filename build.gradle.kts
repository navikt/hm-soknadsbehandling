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
    implementation(libs.kotlin.stdlib)
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

    // Test
    testImplementation(libs.bundles.ktor.server.test)
    testImplementation(libs.tbdLibs.rapidsAndRivers.test)
    testImplementation(libs.handlebars)
    testImplementation(libs.jackson.dataformat.yaml)
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
