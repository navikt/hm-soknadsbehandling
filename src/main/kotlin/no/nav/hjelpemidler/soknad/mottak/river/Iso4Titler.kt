package no.nav.hjelpemidler.soknad.mottak.river

// TODO: dette bør på sikt ligge i grunndata
val ISO4_TITLER = mapOf<String, String>(
    "1203" to "Ganghjelpemiddel", // Ganghjelpemidler som håndteres med én arm
    "1206" to "Ganghjelpemiddel", // Ganghjelpemidler som håndteres med begge armene
    "1207" to "Ganghjelpemiddel", // Tilleggsutstyr til ganghjelpemidler
    "1210" to "Kjøretøy", // Biler, minibusser og lastebiler
    "1211" to "Kjøretøy", // Kjøretøy for kollektivtrafikk
    "1212" to "Kjøretøy", // Biltilpasninger og -tilbehør
    "1216" to "Kjøretøy", // Mopeder og motorsykler
    "1217" to "Kjøretøy", // Andre motoriserte kjøretøy
    "1218" to "Sykkel", // Sykler
    "1222" to "Manuell rullestol", // Manuelle rullestoler
    "1223" to "Motordrevet rullestol", // Motordrevne rullestoler
    "1224" to "Rullestol", // Tilleggsutstyr til rullestoler
    "1227" to "Kjøretøy uten motor", // Andre kjøretøy uten motor
    "1231" to "Kropsstilling", // Hjelpemidler for å endre kroppsstilling
    "1236" to "Personløfter", // Personløftere
    "1239" to "Orientering", // Orienteringshjelpemidler
    "1503" to "Kjøkkenhjelpemiddel", // Hjelpemidler for tilberedning av mat og drikke
    "1506" to "Oppvask", // Oppvaskhjelpemidler
    "1509" to "Spising og drikking", // Hjelpemidler for spising og drikking
    "1512" to "Rengjøringshjelpemiddel", // Rengjøringshjelpemidler
    "1515" to "Håndarbeidshjelpemiddel", // Hjelpemidler for å lage eller vedlikeholde tekstiler for bruk i hjemmet
    "1518" to "Hagearbeid", // Hjelpemidler for hage- og plenpleie til hjemmebruk
    "1803" to "Bord", // Bord
    "1806" to "Lysarmaturer", // Lysarmaturer
    "1809" to "Sittemøbler", // Sittemøbler
    "1810" to "Sittemøbler", // Tilleggsutstyr til sittemøbler
    "1812" to "Senger og sengeutstyr", // Senger og sengeutstyr
    "1815" to "Høyderegulering", // Hjelpemidler for høyderegulering av møbler
    "1818" to "Rekkverk", // Håndlister og støttehåndtak
    "1821" to "Åpnere og lukkere", // Åpnere og lukkere til porter, dører, vinduer og gardiner
    "1824" to "Byggeelementer", // Byggeelementer ved tilpasning av boliger og andre lokaler
    "1830" to "Vertikal forflytning", // Hjelpemidler for vertikal forflytning
    "1833" to "Sikkerhetsutstyr bolig", // Sikkerhetsutstyr til boliger og andre lokaler
    "1836" to "Oppbevaringsmøbler", // Oppbevaringsmøbler
    "2203" to "Synshjelpemidler", // Synshjelpemidler
    "2206" to "Hørselshjelpemidler", // Hørselshjelpemidler
    "2209" to "Talehjelpemidler", // Talehjelpemidler
    "2212" to "Tegning og skriving", // Hjelpemidler for tegning og skriving
    "2215" to "Regning", // Hjelpemidler for regning
    "2218" to "Medieutstyr", // Hjelpemidler som tar opp, spiller av og viser fram informasjon i form av lyd og bilde
    "2221" to "Nærkommunikasjon", // Hjelpemidler for nærkommunikasjon
    "2224" to "Telekommunikasjon", // Hjelpemidler for telefonering og andre former for telekommunikasjon
    "2227" to "Varslingshjelpemiddel", // Hjelpemidler for varsling og alarmering
    "2230" to "Lesehjelpemidler", // Lesehjelpemidler
    "2233" to "Datautstyr", // Datamaskiner og terminaler
    "2236" to "Datautstyr", // Input-enheter for datamaskiner
    "2239" to "Datautstyr", // Output-enheter for datamaskiner
    "2242" to "Datautstyr", // Interaktive enheter for datamaskiner
    "2290" to "Programvare", // Programvare til flere formål
    "2406" to "Håndtering av beholdere", // Hjelpemidler for håndtering av beholdere
    "2409" to "Betjene utstyr", // Systemer for betjening og kontroll av utstyr
    "2413" to "Fjernstyringssystem", // Hjelpemidler for fjernstyring og omgivelseskontroll
    "2418" to "Støtte eller erstatte armfunksjon", // Hjelpemidler som støtter eller erstatter arm-, hånd- eller fingerfunksjon eller en kombinasjon av disse funksjonene
    "2421" to "Rekkevidde", // Hjelpemidler for økt rekkevidde
    "2424" to "Plassering", // Hjelpemidler for plassering
    "2427" to "Fikseringshjelpemidler", // Fikseringshjelpemidler
    "2436" to "Bæring og transport", // Hjelpemidler for bæring og transport
    "2439" to "Beholdere", // Beholdere for oppbevaring av gjenstander
    "2703" to "Miljøforbedring", // Hjelpemidler for miljøforbedringer
    "2706" to "Måleinstrumenter", // Måleinstrumenter
    // "2803" to "", // Møbler og innredning på arbeidsplasser
    // "2806" to "", // Hjelpemidler for transport av gjenstander på arbeidsplasser
    // "2809" to "", // Hjelpemidler for løfting og flytting av gjenstander på arbeidsplasser
    // "2812" to "", // Hjelpemidler for å feste, nå og gripe gjenstander på arbeidsplasser
    // "2815" to "", // Maskiner og verktøy til bruk på arbeidsplasser
    // "2818" to "", // Innretninger for prøving og overvåking på arbeidsplasser
    // "2821" to "", // Hjelpemidler for kontoradministrasjon og lagring og styring av informasjon på arbeidsplasser
    // "2824" to "", // Hjelpemidler for vern av helse og sikkerhet på arbeidsplasser
    // "2827" to "", // Hjelpemidler for yrkesmessig vurdering og opplæring
    "3003" to "Leke", // Hjelpemidler for lek
    "3009" to "Sportsaktivitet", // Hjelpemidler for sportsaktiviteter
    // "3012" to "", // Hjelpemidler for å spille og komponere musikk
    // "3015" to "", // Hjelpemidler for produksjon av bilder, filmer og videoer
    // "3018" to "", // Verktøy, materialer og utstyr til håndverk
    // "3024" to "", // Hjelpemidler for jakt og fiske
    // "3027" to "", // Hjelpemidler for camping
    "3030" to "Røykehjelpemiddel", // Hjelpemidler for røyking
    // "3034" to "", // Hjelpemidler for pleie og stell av dyr
    // "0403" to "", // Hjelpemidler for respirasjon
    // "0406" to "", // Hjelpemidler for sirkulasjonsbehandling
    // "0408" to "", // Hjelpemidler for å stimulere kroppskontroll og kroppsbevissthet
    // "0409" to "", // Hjelpemidler for lysbehandling
    // "0415" to "", // Hjelpemidler for dialysebehandling
    // "0419" to "", // Hjelpemidler for administrering av medisiner
    "0422" to "Steriliseringsutstyr", // Steriliseringsutstyr
    // "0424" to "", // Hjelpemidler og materiell til fysisk, fysiologisk og biokjemisk testing
    // "0425" to "", // Utstyr og materiell til kognitive tester
    "0426" to "Kognitiv behandling", // Hjelpemidler for kognitiv behandling
    "0427" to "Stimulatorer", // Stimulatorer
    "0430" to "Varme-/kuldebehandling", // Hjelpemidler for varme- og/eller kuldebehandling
    // "0433" to "", // Hjelpemidler beregnet på å bevare vevet intakt
    "0436" to "Sansetrening", // Hjelpemidler for persepsjonstrening (sansetrening)
    // "0445" to "", // Hjelpemidler for traksjonsbehandling av ryggsøylen
    "0448" to "Treningshjelpemiddel", // Hjelpemidler for trening av bevegelse, styrke og balanse
    "0449" to "Sårbehandling", // Hjelpemidler for sårbehandling
    "0503" to "Treningshjelpemiddel", // Hjelpemidler for kommunikasjonsterapi og -trening
    "0506" to "Treningshjelpemiddel", // Hjelpemidler for trening av alternativ og supplerende kommunikasjon
    "0509" to "Treningshjelpemiddel", // Hjelpemidler for kontinenstrening
    "0512" to "Treningshjelpemiddel", // Hjelpemidler for å trene på kognitive ferdigheter
    "0515" to "Treningshjelpemiddel", // Hjelpemidler for å trene på grunnleggende ferdigheter
    "0518" to "Treningshjelpemiddel", // Hjelpemidler for å trene på ulike utdanningsfag
    "0524" to "Treningshjelpemiddel", // Hjelpemidler for å trene på kunstneriske fag
    "0527" to "Treningshjelpemiddel", // Hjelpemidler for trening av sosiale ferdigheter
    "0530" to "Treningshjelpemiddel", // Hjelpemidler for å trene på styring av input-enheter og håndtering av produkter og varer
    "0533" to "Treningshjelpemiddel", // Hjelpemidler for å trene på aktiviteter i dagliglivet
    "0536" to "Treningshjelpemiddel", // Hjelpemidler for trening av å endre og opprettholde kroppsstilling
    "0603" to "Spinal-/kranieortoser", // Spinal- og kranieortoser
    "0604" to "Abdominale ortoser", // Abdominale ortoser
    "0606" to "Armortoser", // Armortoser
    "0612" to "Benortoser", // Benortoser
    "0615" to "Nervestimulator/hybrid ortose", // Funksjonelle nevromuskulære stimulatorer (FNS) og hybride ortoser
    "0618" to "Armproteser", // Armproteser
    "0624" to "Benproteser", // Benproteser
    "0630" to "Proteser", // Andre proteser enn til armer og ben
    "0903" to "Klær", // Klær og sko
    "0906" to "Kroppsbeskyttelse", // Kroppsbåret beskyttelsesmateriell
    "0907" to "Kroppsstabilisering", // Hjelpemidler for stabilisering av kroppen
    "0909" to "Av-/påkledning", // Hjelpemidler for av- og påkledning
    "0912" to "Toalettbesøk", // Hjelpemidler for toalettbesøk
    // "0915" to "", // Hjelpemidler for trakeostomi
    "0918" to "Stomihjelpemidler", // Stomihjelpemidler
    // "0921" to "", // Produkter til beskyttelse og rengjøring av hud
    // "0924" to "", // Urindrenerende hjelpemidler
    // "0927" to "", // Hjelpemidler for oppsamling av urin og avføring
    // "0930" to "", // Hjelpemidler for absorbering av urin og avføring
    // "0931" to "", // Hjelpemidler for å forhindre ufrivillig urin- eller avføringslekkasje
    "0932" to "Menstruasjonshjelpemiddel", // Hjelpemidler for håndtering av menstruasjon
    "0933" to "Kroppsvask", // Hjelpemidler for kroppsvask, bading og dusjing
    "0936" to "Hånd-/fotpleie", // Hjelpemidler for hånd- og fotpleie
    "0939" to "Hårpleie", // Hjelpemidler for hårpleie
    "0942" to "Tannpleie", // Hjelpemidler for tannpleie
    "0945" to "Ansiktspleie", // Hjelpemidler for ansiktspleie
    "0954" to "Seksualhjelpemiddel", // Hjelpemidler for seksuelle aktiviteter
)