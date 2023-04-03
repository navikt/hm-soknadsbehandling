import com.expediagroup.graphql.plugin.gradle.tasks.GraphQLIntrospectSchemaTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

val fuelVersion = "2.3.1"
val graphQLClientVersion = "6.1.0"

plugins {
    application
    kotlin("jvm") version "1.7.20"
    id("com.expediagroup.graphql") version "6.1.0"
    id("com.diffplug.spotless") version "6.2.0"
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

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

val ktorVersion = "2.2.4"
fun ktor(name: String) = "io.ktor:ktor-$name:$ktorVersion"
fun graphqlKotlin(name: String) = "com.expediagroup:graphql-kotlin-$name:$graphQLClientVersion"

dependencies {
    // R&R and Logging fixes
    implementation("com.github.navikt:rapids-and-rivers:2022111011111668075098.65e508dcde8b") {
        exclude(group = "ch.qos.logback", module = "logback-classic")
        exclude(group = "net.logstash.logback", module = "logstash-logback-encoder")
    }
    api("ch.qos.logback:logback-classic:1.4.4")
    api("net.logstash.logback:logstash-logback-encoder:7.2") {
        exclude("com.fasterxml.jackson.core")
    }

    implementation("io.github.microutils:kotlin-logging:3.0.4")

    // Kotlin
    implementation(kotlin("stdlib-jdk8"))
    implementation(kotlin("reflect"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.6.4") // følger ikke kotlin-versjon

    // Other
    implementation("com.natpryce:konfig:1.6.10.0")
    implementation("com.github.guepardoapps:kulid:2.0.0.0")
    implementation("com.github.tomakehurst:wiremock-standalone:2.27.2")

    // Jackson
    val jacksonVersion = "2.14.0"
    implementation("com.fasterxml.jackson.core:jackson-databind:$jacksonVersion")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jacksonVersion")

    // Ktor Client
    implementation(ktor("client-core"))
    implementation(ktor("client-apache"))
    implementation(ktor("serialization-jackson"))

    // Fuel -> todo: fjern, bruk ktor-client som også er i bruk
    implementation("com.github.kittinunf.fuel:fuel:$fuelVersion")
    implementation("com.github.kittinunf.fuel:fuel-coroutines:$fuelVersion")

    // InfluxDB
    implementation("org.influxdb:influxdb-java:2.23")
    implementation("com.influxdb:influxdb-client-kotlin:6.7.0")

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
    testImplementation("io.mockk:mockk:1.13.2")
    val kotestVersion = "5.5.4"
    testImplementation("io.kotest:kotest-assertions-core:$kotestVersion")
    testImplementation("io.kotest:kotest-runner-junit5:$kotestVersion")
}

spotless {
    kotlin {
        ktlint()
        targetExclude("**/generated/**")
    }
    kotlinGradle {
        target("*.gradle.kts")
        ktlint()
    }
}

tasks.withType<KotlinCompile> {
    dependsOn("spotlessApply")
    dependsOn("spotlessCheck")

    kotlinOptions.jvmTarget = "17"
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.withType<Jar> {
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    manifest {
        attributes["Main-Class"] = application.mainClass
    }
    from(
        configurations.runtimeClasspath.get().map {
            if (it.isDirectory) it else zipTree(it)
        }
    )
}

graphql {
    client {
        schemaFile = file("src/main/resources/hmdb/schema.graphql")
        queryFileDirectory = "src/main/resources/hmdb"
        packageName = "no.nav.hjelpemidler.soknad.mottak.client.hmdb"
    }
}

val graphqlIntrospectSchema by tasks.getting(GraphQLIntrospectSchemaTask::class) {
    endpoint.set("https://hm-grunndata-api.dev.intern.nav.no/graphql")
    outputFile.set(file("src/main/resources/hmdb/schema.graphql"))
}
