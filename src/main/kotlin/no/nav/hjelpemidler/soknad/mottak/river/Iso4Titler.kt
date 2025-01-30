package no.nav.hjelpemidler.soknad.mottak.river

// TODO: dette bør på sikt ligge i grunndata
val ISO4_TITLER = mapOf<String, String>(
    "1203" to "Ganghjelpemiddel", // Ganghjelpemidler som håndteres med én arm
    "1206" to "Ganghjelpemiddel", // Ganghjelpemidler som håndteres med begge armene
    "1207" to "Ganghjelpemiddel", // Tilleggsutstyr til ganghjelpemidler
    // "1210" to "", // Biler, minibusser og lastebiler
    // "1211" to "", // Kjøretøy for kollektivtrafikk
    // "1212" to "", // Biltilpasninger og -tilbehør
    // "1216" to "", // Mopeder og motorsykler
    // "1217" to "", // Andre motoriserte kjøretøy
    // "1218" to "", // Sykler
    "1222" to "Manuell rullestol", // Manuelle rullestoler
    "1223" to "Motordrevet rullestol", // Motordrevne rullestoler
    "1224" to "Rullestol", // Tilleggsutstyr til rullestoler
    // "1227" to "", // Andre kjøretøy uten motor
    // "1231" to "", // Hjelpemidler for å endre kroppsstilling
    "1236" to "Personløfter", // Personløftere
    // "1239" to "", // Orienteringshjelpemidler
    // "1503" to "", // Hjelpemidler for tilberedning av mat og drikke
    // "1506" to "", // Oppvaskhjelpemidler
    // "1509" to "", // Hjelpemidler for spising og drikking
    // "1512" to "", // Rengjøringshjelpemidler
    // "1515" to "", // Hjelpemidler for å lage eller vedlikeholde tekstiler for bruk i hjemmet
    // "1518" to "", // Hjelpemidler for hage- og plenpleie til hjemmebruk
    // "1803" to "", // Bord
    // "1806" to "", // Lysarmaturer
    "1809" to "Sittemøbel", // Sittemøbler
    "1810" to "Sittemøbel", // Tilleggsutstyr til sittemøbler
    "1812" to "Seng", // Senger og sengeutstyr
    // "1815" to "", // Hjelpemidler for høyderegulering av møbler
    // "1818" to "", // Håndlister og støttehåndtak
    // "1821" to "", // Åpnere og lukkere til porter, dører, vinduer og gardiner
    // "1824" to "", // Byggeelementer ved tilpasning av boliger og andre lokaler
    // "1830" to "", // Hjelpemidler for vertikal forflytning
    // "1833" to "", // Sikkerhetsutstyr til boliger og andre lokaler
    // "1836" to "", // Oppbevaringsmøbler
    // "2203" to "", // Synshjelpemidler
    // "2206" to "", // Hørselshjelpemidler
    // "2209" to "", // Talehjelpemidler
    // "2212" to "", // Hjelpemidler for tegning og skriving
    // "2215" to "", // Hjelpemidler for regning
    // "2218" to "", // Hjelpemidler som tar opp, spiller av og viser fram informasjon i form av lyd og bilde
    // "2221" to "", // Hjelpemidler for nærkommunikasjon
    // "2224" to "", // Hjelpemidler for telefonering og andre former for telekommunikasjon
    // "2227" to "", // Hjelpemidler for varsling og alarmering
    // "2230" to "", // Lesehjelpemidler
    // "2233" to "", // Datamaskiner og terminaler
    // "2236" to "", // Input-enheter for datamaskiner
    // "2239" to "", // Output-enheter for datamaskiner
    // "2242" to "", // Interaktive enheter for datamaskiner
    // "2290" to "", // Programvare til flere formål
    // "2406" to "", // Hjelpemidler for håndtering av beholdere
    // "2409" to "", // Systemer for betjening og kontroll av utstyr
    // "2413" to "", // Hjelpemidler for fjernstyring og omgivelseskontroll
    // "2418" to "", // Hjelpemidler som støtter eller erstatter arm-, hånd- eller fingerfunksjon eller en kombinasjon av disse funksjonene
    // "2421" to "", // Hjelpemidler for økt rekkevidde
    // "2424" to "", // Hjelpemidler for plassering
    // "2427" to "", // Fikseringshjelpemidler
    // "2436" to "", // Hjelpemidler for bæring og transport
    // "2439" to "", // Beholdere for oppbevaring av gjenstander
    //"2703" to "", // Hjelpemidler for miljøforbedringer
    // "2706" to "", // Måleinstrumenter
    // "2803" to "", // Møbler og innredning på arbeidsplasser
    // "2806" to "", // Hjelpemidler for transport av gjenstander på arbeidsplasser
    // "2809" to "", // Hjelpemidler for løfting og flytting av gjenstander på arbeidsplasser
    // "2812" to "", // Hjelpemidler for å feste, nå og gripe gjenstander på arbeidsplasser
    // "2815" to "", // Maskiner og verktøy til bruk på arbeidsplasser
    // "2818" to "", // Innretninger for prøving og overvåking på arbeidsplasser
    // "2821" to "", // Hjelpemidler for kontoradministrasjon og lagring og styring av informasjon på arbeidsplasser
    // "2824" to "", // Hjelpemidler for vern av helse og sikkerhet på arbeidsplasser
    // "2827" to "", // Hjelpemidler for yrkesmessig vurdering og opplæring
    // "3003" to "", // Hjelpemidler for lek
    // "3009" to "", // Hjelpemidler for sportsaktiviteter
    // "3012" to "", // Hjelpemidler for å spille og komponere musikk
    // "3015" to "", // Hjelpemidler for produksjon av bilder, filmer og videoer
    // "3018" to "", // Verktøy, materialer og utstyr til håndverk
    // "3024" to "", // Hjelpemidler for jakt og fiske
    // "3027" to "", // Hjelpemidler for camping
    // "3030" to "", // Hjelpemidler for røyking
    // "3034" to "", // Hjelpemidler for pleie og stell av dyr
    // "0403" to "", // Hjelpemidler for respirasjon
    // "0406" to "", // Hjelpemidler for sirkulasjonsbehandling
    // "0408" to "", // Hjelpemidler for å stimulere kroppskontroll og kroppsbevissthet
    // "0409" to "", // Hjelpemidler for lysbehandling
    // "0415" to "", // Hjelpemidler for dialysebehandling
    // "0419" to "", // Hjelpemidler for administrering av medisiner
    // "0422" to "", // Steriliseringsutstyr
    // "0424" to "", // Hjelpemidler og materiell til fysisk, fysiologisk og biokjemisk testing
    // "0425" to "", // Utstyr og materiell til kognitive tester
    // "0426" to "", // Hjelpemidler for kognitiv behandling
    // "0427" to "", // Stimulatorer
    // "0430" to "", // Hjelpemidler for varme- og/eller kuldebehandling
    // "0433" to "", // Hjelpemidler beregnet på å bevare vevet intakt
    // "0436" to "", // Hjelpemidler for persepsjonstrening (sansetrening)
    // "0445" to "", // Hjelpemidler for traksjonsbehandling av ryggsøylen
    // "0448" to "", // Hjelpemidler for trening av bevegelse, styrke og balanse
    // "0449" to "", // Hjelpemidler for sårbehandling
    // "0503" to "", // Hjelpemidler for kommunikasjonsterapi og -trening
    // "0506" to "", // Hjelpemidler for trening av alternativ og supplerende kommunikasjon
    // "0509" to "", // Hjelpemidler for kontinenstrening
    // "0512" to "", // Hjelpemidler for å trene på kognitive ferdigheter
    // "0515" to "", // Hjelpemidler for å trene på grunnleggende ferdigheter
    // "0518" to "", // Hjelpemidler for å trene på ulike utdanningsfag
    // "0524" to "", // Hjelpemidler for å trene på kunstneriske fag
    // "0527" to "", // Hjelpemidler for trening av sosiale ferdigheter
    // "0530" to "", // Hjelpemidler for å trene på styring av input-enheter og håndtering av produkter og varer
    // "0533" to "", // Hjelpemidler for å trene på aktiviteter i dagliglivet
    // "0536" to "", // Hjelpemidler for trening av å endre og opprettholde kroppsstilling
    // "0603" to "", // Spinal- og kranieortoser
    // "0604" to "", // Abdominale ortoser
    // "0606" to "", // Armortoser
    // "0612" to "", // Benortoser
    // "0615" to "", // Funksjonelle nevromuskulære stimulatorer (FNS) og hybride ortoser
    // "0618" to "", // Armproteser
    // "0624" to "", // Benproteser
    // "0630" to "", // Andre proteser enn til armer og ben
    // "0903" to "", // Klær og sko
    // "0906" to "", // Kroppsbåret beskyttelsesmateriell
    // "0907" to "", // Hjelpemidler for stabilisering av kroppen
    // "0909" to "", // Hjelpemidler for av- og påkledning
    // "0912" to "", // Hjelpemidler for toalettbesøk
    // "0915" to "", // Hjelpemidler for trakeostomi
    // "0918" to "", // Stomihjelpemidler
    // "0921" to "", // Produkter til beskyttelse og rengjøring av hud
    // "0924" to "", // Urindrenerende hjelpemidler
    // "0927" to "", // Hjelpemidler for oppsamling av urin og avføring
    // "0930" to "", // Hjelpemidler for absorbering av urin og avføring
    // "0931" to "", // Hjelpemidler for å forhindre ufrivillig urin- eller avføringslekkasje
    // "0932" to "", // Hjelpemidler for håndtering av menstruasjon
    // "0933" to "", // Hjelpemidler for kroppsvask, bading og dusjing
    // "0936" to "", // Hjelpemidler for hånd- og fotpleie
    // "0939" to "", // Hjelpemidler for hårpleie
    // "0942" to "", // Hjelpemidler for tannpleie
    // "0945" to "", // Hjelpemidler for ansiktspleie
    // "0954" to "", // Hjelpemidler for seksuelle aktiviteter
)