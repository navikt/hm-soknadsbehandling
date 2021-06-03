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

# Papirsoknads lifecycle

Den fyrste statusen ein papirsøknad får er ENDELIG_JOURNALFØRT når vi mottek ei endeleg journalføring-melding frå Joark.
Vidare vil den få ein status idet det blir fatta eit vedtak for saka: VEDTAKSRESULTAT_INNVILGET, VEDTAKSRESULTAT_MUNTLIG_INNVILGET, VEDTAKSRESULTAT_DELVIS_INNVILGET, VEDTAKSRESULTAT_AVSLÅTT, VEDTAKSRESULTAT_ANNET
Til slutt får den status når vi mottek melding om at hjelpemiddelet er på veg frå OEBS (fyrste ordrelinja vi får frå OEBS vil endre statusen): UTSENDING_STARTET (edited) 

# Henvendelser

Spørsmål knyttet til koden eller prosjektet kan stilles som issues her på GitHub.

## For NAV-ansatte

Interne henvendelser kan sendes via Slack i kanalen #digihot-dev.
