package no.nav.hjelpemidler.soknad.mottak.service

import com.fasterxml.jackson.databind.JsonNode
import java.util.Date
import java.util.UUID

data class SoknadDataDto(
    val fnrBruker: String? = null,
    val navnBruker: String? = null,
    val fnrInnsender: String? = null,
    val soknadId: UUID? = null,
    val soknad: JsonNode? = null,
    val status: Status? = null,
    val kommunenavn: String? = null
)

data class SoknadMedStatus(
    val soknadId: UUID? = null,
    val datoOpprettet: Date? = null,
    val datoOppdatert: Date? = null,
    val status: Status? = null,
    val fullmakt: Boolean? = null,
    val formidlerNavn: String? = null
)

class SøknadForBrukerDto constructor(
    val søknadId: UUID? = null,
    val datoOpprettet: Date? = null,
    var datoOppdatert: Date? = null,
    val status: Status? = null,
    val fullmakt: Boolean? = null,
    val fnrBruker: String? = null,
    val søknadsdata: SøknadsdataDto? = null,

)

class SøknadsdataDto(
    val bruker: BrukerDto? = null,
    val formidler: FormidlerDto? = null,
    val hjelpemidler: List<HjelpemiddelDto>? = null,
    val hjelpemiddelTotalAntall: Int? = null,
    val oppfolgingsansvarlig: OppfolgingsansvarligDto? = null,
    val levering: LeveringDto? = null,
)

class BrukerDto(
    val etternavn: String? = null,
    val fnummer: String? = null,
    val fornavn: String? = null,
    val telefonNummer: String? = null,
    val adresse: String? = null,
    val postnummer: String? = null,
    val poststed: String? = null,
    val boform: String? = null,
    val bruksarena: BruksarenaDto? = null,
    val funksjonsnedsettelser: List<FunksjonsnedsettelseDto>? = null,
    val signatur: SignaturTypeDto? = null,
)

enum class SignaturTypeDto { BRUKER_BEKREFTER, FULLMAKT }
enum class BruksarenaDto { DAGLIGLIVET, UKJENT }
enum class FunksjonsnedsettelseDto { BEVEGELSE, HØRSEL, KOGNISJON }

class FormidlerDto(
    val navn: String? = null,
    val arbeidssted: String? = null,
    val stilling: String? = null,
    val adresse: String? = null,
    val telefon: String? = null,
    val treffesEnklest: String? = null,
    val epost: String? = null,
    val kommunenavn: String? = null,
)

class OppfolgingsansvarligDto(
    val navn: String? = null,
    val arbeidssted: String? = null,
    val stilling: String? = null,
    val telefon: String? = null,
    val ansvarFor: String? = null,
)

class HjelpemiddelDto(
    val antall: Int? = null,
    val beskrivelse: String? = null,
    val hjelpemiddelkategori: String? = null,
    val hmsNr: String? = null,
    val tilleggsinformasjon: String? = null,
    var rangering: String? = null,
    val utlevertFraHjelpemiddelsentralen: Boolean? = null,
    val vilkarliste: List<HjelpemiddelVilkarDto>? = null,
    val tilbehorListe: List<TilbehorDto>? = null,
    val begrunnelse: String? = null,
    val kanIkkeTilsvarande: Boolean? = null,
    val navn: String? = null,
)

class LeveringDto(
    val kontaktPerson: KontaktPersonDto? = null,
    val leveringsmaate: LeveringsmaateDto? = null,
    val adresse: String? = null,
    val merknad: String? = null,
)

class KontaktPersonDto(
    val navn: String? = null,
    val telefon: String? = null,
    val kontaktpersonType: KontaktpersonTypeDto? = null
)

enum class LeveringsmaateDto {
    FOLKEREGISTRERT_ADRESSE, ANNEN_ADRESSE, HJELPEMIDDELSENTRAL, ALLEREDE_LEVERT
}

enum class KontaktpersonTypeDto {
    HJELPEMIDDELBRUKER, HJELPEMIDDELFORMIDLER, ANNEN_KONTAKTPERSON, INGEN_KONTAKTPERSON
}

class HjelpemiddelVilkarDto(
    val vilkaarTekst: String? = null,
    val tilleggsInfo: String? = null
)

class TilbehorDto(
    val hmsnr: String? = null,
    val antall: Int? = null,
    val navn: String? = null
)

class UtgåttSøknad(
    val søknadId: UUID? = null,
    val status: Status? = null,
    val fnrBruker: String? = null
)
