dependencyResolutionManagement {
    @Suppress("UnstableApiUsage")
    repositories {
        mavenCentral()
        maven("https://jitpack.io")
        maven {
            url = uri("https://maven.pkg.github.com/navikt/*")
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                password = System.getenv("GITHUB_TOKEN")
            }
        }
        maven {
            url = uri("https://github-package-registry-mirror.gc.nav.no/cached/maven-release")
        }
    }
    versionCatalogs {
        create("libs") {
            from("no.nav.hjelpemidler:hm-katalog:0.3.13")
            version("behovsmeldingsmodell", "24.232.090208")
        }
    }
}

rootProject.name = "hm-soknadsbehandling"
