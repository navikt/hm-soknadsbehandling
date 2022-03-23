# README
![build-deploy-dev](https://github.com/navikt/hm-soknadsbehandling/workflows/Build%20and%20deploy/badge.svg)

App som lytter på rapid og lagrer innsendte søknader i database og sender videre på rapid.


# Lokal køyring

1. Kjør docker-compose i [hm-soknad-api](https://github.com/navikt/hm-soknad-api) for å starte nødvendig økosystem:
```
cd hm-soknad-api
docker-compose -f docker-compose/docker-compose.yml up
```
2. Start hm-soknadsbehandling-db [hm-soknadsbehandling-db](https://github.com/navikt/hm-soknadsbehandling-db)

3. Start hm-soknadsbehandling gjennom Application run configuration i Idea

Dersom du vil legge nye søknader på rapid så kan du følge readme i [hm-soknad-api](https://github.com/navikt/hm-soknad-api) og
[hm-soknad](https://github.com/navikt/hm-soknad)

# Statusendringar for søknadar
Etter at ein søknad kjem inn vil den vere innom ulike statusar. Nokon av dei gjeld kun for digitale søknadar, då spesielt relatert til signering
med fullmakt eller brukargodkjenning. Når det gjeld fatting av vedtak i Infotrygd og utsending av produkt frå OEBS er det derimot likt for 
papirsøknadar og digitale søknadar. 

Merk at ENDELIG_JOURNALFØRT av kvalitetssikringsgrunnar er den fyrste statusen ein papirsøknad er innom. Papirsøknadar treng ekstra kontrollsikring av 
at søknaden og dokumenta i søknaden tilhøyrer rett person, og at informasjon er korrekt.

#### Status knytta til digital søknad der formidlar har to ulike val for signeringsform
- GODKJENT_MED_FULLMAKT: Søknad sendt inn med fullmakt.
- VENTER_GODKJENNING: Søknad sendt inn med brukargodkjenning. Søknaden er ikkje godkjend av brukar enda.
- GODKJENT: Søknad sendt inn med brukargodkjenning. Godkjend av brukar før utløpsfristen.
- SLETTET: Søknad sendt inn med brukargodkjenning. Sletta av brukar fordi dei ikkje er samde i innhaldet.
- UTLØPT: Søknad sendt inn med brukargodkjenning. Ikkje godkjend innan fristen på 14 dagar.
  
#### Statusar som er like for både digitale søknadar og papirsøknadar
- ENDELIG_JOURNALFØRT: Søknaden er journalført inne i Gosys, og vi mottek ei melding om dette frå Joark. Dette er fyrste status for ein papirsøknad.
- VEDTAKSRESULTAT_INNVILGET: Saka har blitt innvilga i Infotrygd, og brukaren får brev i posten
- VEDTAKSRESULTAT_MUNTLIG_INNVILGET: Saka har blitt munnleg innvilga i Infotrygd. Brukaren får ikkje brev i posten. 
- VEDTAKSRESULTAT_DELVIS_INNVILGET: Saka er delvis innvilga i Infotrygd. Brukaren får noko av det som er søkt om, men ikkje alt.
- VEDTAKSRESULTAT_AVSLÅTT: Saka har fått avslag i Infotrygd sidan kriteria for utlån ikkje er møtt.
- VEDTAKSRESULTAT_HENLAGTBORTFALT: Dei må kontakte NAV Hjelpemiddelsentral for å få vite meir.
- VEDTAKSRESULTAT_ANNET: Samlepost for ulike resultat i Infotrygd. Dei må kontakte NAV Hjelpemiddelsentral for å få vite meir.
- UTSENDING_STARTET: Fyrste ordrelinje mottatt frå OEBS. Utsending av hjelpemiddel til kommunen har starta.

# Henvendelser

Spørsmål knyttet til koden eller prosjektet kan stilles som issues her på GitHub.

## For NAV-ansatte

Interne henvendelser kan sendes via Slack i kanalen #digihot-dev.
