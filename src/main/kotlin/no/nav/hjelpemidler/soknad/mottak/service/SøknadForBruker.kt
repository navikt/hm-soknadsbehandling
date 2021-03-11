package no.nav.hjelpemidler.soknad.mottak.service

import com.fasterxml.jackson.databind.JsonNode
import java.util.Date
import java.util.UUID

class SøknadForBruker private constructor(
    val søknadId: UUID,
    val datoOpprettet: Date,
    var datoOppdatert: Date,
    val status: Status,
    val fnrBruker: String,
    val søknadsdata: Søknadsdata?
) {

    companion object {
        fun new(søknadId: UUID, datoOpprettet: Date, datoOppdatert: Date, søknad: JsonNode, status: Status, kommunenavn: String?, fnrBruker: String) =
            SøknadForBruker(søknadId, datoOpprettet, datoOppdatert, status, fnrBruker, Søknadsdata(søknad, kommunenavn))
        fun newEmptySøknad(søknadId: UUID, datoOpprettet: Date, datoOppdatert: Date, status: Status, fnrBruker: String) =
            SøknadForBruker(søknadId, datoOpprettet, datoOppdatert, status, fnrBruker, null)
    }
}

private fun bruker(søknad: JsonNode): Bruker {
    val brukerNode = søknad["soknad"]["bruker"]
    val brukerSituasjonNode = søknad["soknad"]["brukersituasjon"]
    return Bruker(
        fnummer = brukerNode["fnummer"].textValue(),
        fornavn = brukerNode["fornavn"].textValue(),
        etternavn = brukerNode["etternavn"].textValue(),
        telefonNummer = brukerNode["telefonNummer"].textValue(),
        adresse = brukerNode["adresse"]?.textValue(),
        postnummer = brukerNode["postnummer"]?.textValue(),
        poststed = brukerNode["poststed"]?.textValue(),
        boform = brukerSituasjonNode["bostedRadioButton"].textValue(),
        bruksarena = if (søknad["soknad"]["brukersituasjon"]["bruksarenaErDagliglivet"].booleanValue()) Bruksarena.DAGLIGLIVET else Bruksarena.UKJENT,
        funksjonsnedsettelser = funksjonsnedsettelser(søknad),
        signatur = signaturType(søknad)
    )
}

private fun formidler(søknad: JsonNode, kommunenavn: String?): Formidler {
    val leveringNode = søknad["soknad"]["levering"]
    return Formidler(
        navn = "${leveringNode["hmfFornavn"].textValue()} ${leveringNode["hmfEtternavn"].textValue()}",
        arbeidssted = leveringNode["hmfArbeidssted"].textValue(),
        stilling = leveringNode["hmfStilling"].textValue(),
        adresse = "${leveringNode["hmfPostadresse"].textValue()} ${leveringNode["hmfPostnr"].textValue()} ${leveringNode["hmfPoststed"].textValue()}",
        telefon = leveringNode["hmfTelefon"].textValue(),
        treffesEnklest = leveringNode["hmfTreffesEnklest"].textValue(),
        epost = leveringNode["hmfEpost"].textValue(),
        kommunenavn = kommunenavn,
    )
}

private fun oppfolgingsansvarlig(søknad: JsonNode): Oppfolgingsansvarlig? {
    val leveringNode = søknad["soknad"]["levering"]

    if (leveringNode["opfRadioButton"].textValue() == "Hjelpemiddelformidler") {
        return null
    }

    return Oppfolgingsansvarlig(
        navn = "${leveringNode["opfFornavn"].textValue()} ${leveringNode["opfEtternavn"].textValue()}",
        arbeidssted = leveringNode["opfArbeidssted"].textValue(),
        stilling = leveringNode["opfStilling"].textValue(),
        telefon = leveringNode["opfTelefon"].textValue(),
        ansvarFor = leveringNode["opfAnsvarFor"].textValue()
    )
}

private fun levering(søknad: JsonNode): Levering {
    val leveringNode = søknad["soknad"]["levering"]
    val leveringsMaate = leveringsMaate(søknad)
    return Levering(
        leveringsmaate = leveringsMaate,
        adresse = if (leveringsMaate == Leveringsmaate.ANNEN_ADRESSE) "${leveringNode["utleveringPostadresse"].textValue()} ${leveringNode["utleveringPostnr"].textValue()} ${leveringNode["utleveringPoststed"].textValue()}" else null,
        kontaktPerson = kontaktPerson(søknad),
        merknad = leveringNode["merknadTilUtlevering"]?.textValue()
    )
}

private fun kontaktPerson(søknad: JsonNode): KontaktPerson {
    val leveringNode = søknad["soknad"]["levering"]
    val kontaktPersonType = kontaktPersonType(søknad)

    return if (kontaktPersonType == KontaktpersonType.ANNEN_KONTAKTPERSON) {
        KontaktPerson(
            navn = "${leveringNode["utleveringFornavn"].textValue()} ${leveringNode["utleveringEtternavn"].textValue()}",
            telefon = leveringNode["utleveringTelefon"].textValue(),
            kontaktpersonType = kontaktPersonType
        )
    } else {
        KontaktPerson(
            kontaktpersonType = kontaktPersonType
        )
    }
}

private fun kontaktPersonType(søknad: JsonNode): KontaktpersonType {
    val leveringNode = søknad["soknad"]["levering"]

    return when (leveringNode["utleveringskontaktpersonRadioButton"]?.textValue()) {
        "Hjelpemiddelbruker" -> KontaktpersonType.HJELPEMIDDELBRUKER
        "Hjelpemiddelformidler" -> KontaktpersonType.HJELPEMIDDELFORMIDLER
        "AnnenKontaktperson" -> KontaktpersonType.ANNEN_KONTAKTPERSON
        else -> KontaktpersonType.INGEN_KONTAKTPERSON
    }
}

private fun signaturType(søknad: JsonNode): SignaturType {
    val brukerNode = søknad["soknad"]["bruker"]

    return when (brukerNode["signatur"].textValue()) {
        "BRUKER_BEKREFTER" -> SignaturType.BRUKER_BEKREFTER
        "FULLMAKT" -> SignaturType.FULLMAKT
        else -> throw RuntimeException("Ugyldig signaturtype")
    }
}

private fun leveringsMaate(søknad: JsonNode): Leveringsmaate {
    val leveringNode = søknad["soknad"]["levering"]

    return when (leveringNode["utleveringsmaateRadioButton"].textValue()) {
        "AnnenBruksadresse" -> Leveringsmaate.ANNEN_ADRESSE
        "FolkeregistrertAdresse" -> Leveringsmaate.FOLKEREGISTRERT_ADRESSE
        "Hjelpemiddelsentralen" -> Leveringsmaate.HJELPEMIDDELSENTRAL
        "AlleredeUtlevertAvNav" -> Leveringsmaate.ALLEREDE_LEVERT
        else -> throw RuntimeException("Ugyldig leveringsmåte")
    }
}

private fun funksjonsnedsettelser(søknad: JsonNode): List<Funksjonsnedsettelse> {
    val funksjonsnedsettelser = mutableListOf<Funksjonsnedsettelse>()

    val funksjonsnedsettelseNode = søknad["soknad"]["brukersituasjon"]["nedsattFunksjonTypes"]
    if (funksjonsnedsettelseNode["bevegelse"].booleanValue()) funksjonsnedsettelser.add(Funksjonsnedsettelse.BEVEGELSE)
    if (funksjonsnedsettelseNode["kognisjon"].booleanValue()) funksjonsnedsettelser.add(Funksjonsnedsettelse.KOGNISJON)
    if (funksjonsnedsettelseNode["horsel"].booleanValue()) funksjonsnedsettelser.add(Funksjonsnedsettelse.HØRSEL)

    return funksjonsnedsettelser
}

private fun hjelpemidler(søknad: JsonNode): List<Hjelpemiddel> {
    val hjelpemidler = mutableListOf<Hjelpemiddel>()
    søknad["soknad"]["hjelpemidler"]["hjelpemiddelListe"].forEach {
        val hjelpemiddel = Hjelpemiddel(
            antall = it["antall"].intValue(),
            beskrivelse = it["beskrivelse"].textValue(),
            hjelpemiddelkategori = it["hjelpemiddelkategori"].textValue(),
            hmsNr = it["hmsNr"].textValue(),
            tilleggsinformasjon = it["tilleggsinformasjon"].textValue(),
            rangering = it["produkt"]["postrank"].textValue(),
            utlevertFraHjelpemiddelsentralen = it["utlevertFraHjelpemiddelsentralen"].booleanValue(),
            vilkarliste = vilkaar(it),
            tilbehorListe = tilbehor(it),
            begrunnelse = it["begrunnelsen"]?.textValue(),
            kanIkkeTilsvarande = it["kanIkkeTilsvarande"].booleanValue(),
            navn = it["navn"]?.textValue()
        )
        hjelpemidler.add(hjelpemiddel)
    }
    return hjelpemidler
}

private fun vilkaar(hjelpemiddel: JsonNode): List<HjelpemiddelVilkar> {
    val vilkarListe = mutableListOf<HjelpemiddelVilkar>()
    hjelpemiddel["vilkarliste"]?.forEach {
        vilkarListe.add(
            HjelpemiddelVilkar(
                vilkaarTekst = it["vilkartekst"].textValue(),
                tilleggsInfo = it["tilleggsinfo"]?.textValue()
            )
        )
    }
    return vilkarListe
}

private fun tilbehor(hjelpemiddel: JsonNode): List<Tilbehor> {
    val tilbehorListe = mutableListOf<Tilbehor>()
    hjelpemiddel["tilbehorListe"]?.forEach {
        tilbehorListe.add(
            Tilbehor(
                hmsnr = it["hmsnr"].textValue(),
                navn = it["navn"].textValue(),
                antall = it["antall"].intValue()
            )
        )
    }
    return tilbehorListe
}

class Søknadsdata(søknad: JsonNode, kommunenavn: String?) {
    val bruker = bruker(søknad)
    val formidler = formidler(søknad, kommunenavn)
    val hjelpemidler = hjelpemidler(søknad)
    val hjelpemiddelTotalAntall = søknad["soknad"]["hjelpemidler"]["hjelpemiddelTotaltAntall"].intValue()
    val oppfolgingsansvarlig = oppfolgingsansvarlig(søknad)
    val levering = levering(søknad)
}

class Bruker(
    val etternavn: String,
    val fnummer: String,
    val fornavn: String,
    val telefonNummer: String,
    val adresse: String?,
    val postnummer: String?,
    val poststed: String?,
    val boform: String,
    val bruksarena: Bruksarena,
    val funksjonsnedsettelser: List<Funksjonsnedsettelse>,
    val signatur: SignaturType
)

enum class SignaturType { BRUKER_BEKREFTER, FULLMAKT }
enum class Bruksarena { DAGLIGLIVET, UKJENT }
enum class Funksjonsnedsettelse { BEVEGELSE, HØRSEL, KOGNISJON }

class Formidler(
    val navn: String,
    val arbeidssted: String,
    val stilling: String,
    val adresse: String,
    val telefon: String,
    val treffesEnklest: String,
    val epost: String,
    val kommunenavn: String?,
)

class Oppfolgingsansvarlig(
    val navn: String,
    val arbeidssted: String,
    val stilling: String,
    val telefon: String,
    val ansvarFor: String
)

class Hjelpemiddel(
    val antall: Int,
    val beskrivelse: String,
    val hjelpemiddelkategori: String,
    val hmsNr: String,
    val tilleggsinformasjon: String,
    var rangering: String?,
    val utlevertFraHjelpemiddelsentralen: Boolean,
    val vilkarliste: List<HjelpemiddelVilkar>?,
    val tilbehorListe: List<Tilbehor>?,
    val begrunnelse: String?,
    val kanIkkeTilsvarande: Boolean,
    val navn: String?,
)

class Levering(
    val kontaktPerson: KontaktPerson,
    val leveringsmaate: Leveringsmaate,
    val adresse: String?,
    val merknad: String?

)

class KontaktPerson(
    val navn: String? = null,
    val telefon: String? = null,
    val kontaktpersonType: KontaktpersonType
)

enum class Leveringsmaate {
    FOLKEREGISTRERT_ADRESSE, ANNEN_ADRESSE, HJELPEMIDDELSENTRAL, ALLEREDE_LEVERT
}

enum class KontaktpersonType {
    HJELPEMIDDELBRUKER, HJELPEMIDDELFORMIDLER, ANNEN_KONTAKTPERSON, INGEN_KONTAKTPERSON
}

class HjelpemiddelVilkar(
    val vilkaarTekst: String,
    val tilleggsInfo: String?
)

class Tilbehor(
    val hmsnr: String,
    val antall: Int?,
    val navn: String
)
