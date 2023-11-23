package no.nav.hjelpemidler.soknad.mottak.service

enum class Signatur {
    BRUKER_BEKREFTER,
    FULLMAKT,
    FRITAK_FRA_FULLMAKT,        // Brukt under covid-19
    IKKE_INNHENTET_FORDI_BYTTE,
}
