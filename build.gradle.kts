import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent

val ktor_version = "1.4.0"

plugins {
    application
    kotlin("jvm") version Kotlin.version
    id(Spotless.spotless) version Spotless.version
    id(Shadow.shadow) version Shadow.version
}

buildscript {
    repositories {
        jcenter()
    }
}

apply {
    plugin(Spotless.spotless)
}

repositories {
    mavenCentral()
    jcenter()
    maven("https://packages.confluent.io/maven/")
    maven("https://jitpack.io")
    mavenLocal()
}

application {
    applicationName = "hm-soknadsbehandling"
    mainClassName = "no.nav.hjelpemidler.soknad.mottak.ApplicationKt"
}

java {
    sourceCompatibility = JavaVersion.VERSION_12
    targetCompatibility = JavaVersion.VERSION_12
}

dependencies {
    implementation(Jackson.core)
    implementation(Jackson.kotlin)
    implementation(Jackson.jsr310)
    implementation("com.github.guepardoapps:kulid:1.1.2.0")
    implementation("com.github.navikt:rapids-and-rivers:20210428115805-514c80c")
    implementation(Ktor.serverNetty)
    implementation(Database.Flyway)
    implementation(Database.HikariCP)
    implementation(Database.Kotlinquery)
    implementation(Database.Postgres)
    implementation(Fuel.fuel)
    implementation(Fuel.library("coroutines"))
    implementation(Konfig.konfig)
    implementation(Kotlin.Logging.kotlinLogging)
    implementation(kotlin("stdlib-jdk8"))
    implementation("io.ktor:ktor-jackson:$ktor_version")
    implementation(Database.VaultJdbc) {
        exclude(module = "slf4j-simple")
        exclude(module = "slf4j-api")
    }
    implementation("io.ktor:ktor-auth:$ktor_version")
    implementation("io.ktor:ktor-auth-jwt:$ktor_version")
    implementation("io.ktor:ktor-client-apache:$ktor_version")
    implementation("io.ktor:ktor-client-jackson:$ktor_version")

    testImplementation(Junit5.api)
    testImplementation(KoTest.assertions)
    testImplementation(KoTest.runner)
    testImplementation(Ktor.ktorTest)
    testImplementation(Mockk.mockk)
    testImplementation(TestContainers.postgresql)
    implementation(Wiremock.standalone)
    testRuntimeOnly(Junit5.engine)
}

spotless {
    kotlin {
        ktlint(Ktlint.version)
    }
    kotlinGradle {
        target("*.gradle.kts", "buildSrc/*.gradle.kts")
        ktlint(Ktlint.version)
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions.freeCompilerArgs = listOf()
    kotlinOptions.jvmTarget = "1.8"
}

tasks.withType<Test> {
    useJUnitPlatform()
    testLogging {
        showExceptions = true
        showStackTraces = true
        exceptionFormat = TestExceptionFormat.FULL
        events = setOf(TestLogEvent.PASSED, TestLogEvent.SKIPPED, TestLogEvent.FAILED)
    }
}

tasks.withType<Wrapper> {
    gradleVersion = "6.2.2"
}

tasks.named("shadowJar") {
    dependsOn("test")
}

tasks.named("jar") {
    dependsOn("test")
}

tasks.named("compileKotlin") {
    dependsOn("spotlessCheck")
}
