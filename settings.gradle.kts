dependencyResolutionManagement {
    @Suppress("UnstableApiUsage")
    repositories {
        maven("https://jitpack.io")
        mavenCentral()
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
            from("no.nav.hjelpemidler:hm-katalog:0.1.43")
        }
    }
}

rootProject.name = "hm-soknadsbehandling"
