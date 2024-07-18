package no.nav.hjelpemidler.soknad.mottak

import no.nav.hjelpemidler.configuration.EnvironmentVariable

object Configuration {
    val DELBESTILLING_API_BASEURL by EnvironmentVariable
    val DELBESTILLING_API_SCOPE by EnvironmentVariable

    val GRUNNDATA_GRAPHQL_URL by EnvironmentVariable

    val INFOTRYGD_PROXY_API_BASEURL by EnvironmentVariable
    val INFOTRYGD_PROXY_API_SCOPE by EnvironmentVariable

    val OPPSLAG_API_BASEURL by EnvironmentVariable

    val PDL_GRAPHQL_URL by EnvironmentVariable
    val PDL_GRAPHQL_SCOPE by EnvironmentVariable

    val POST_DOKUMENTBESKRIVELSE_TO_SLACK by EnvironmentVariable

    val SOKNADSBEHANDLING_API_BASEURL by EnvironmentVariable
    val SOKNADSBEHANDLING_API_SCOPE by EnvironmentVariable
}
