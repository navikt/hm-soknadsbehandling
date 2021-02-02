# README
![build-deploy-dev](https://github.com/navikt/hm-soknadsbehandling/workflows/Build%20and%20deploy/badge.svg)

App som lytter på rapid og lagrer innsendte søknader i database og sender videre på rapid.


# Lokal køyring

Kjør docker-compose for å starte database lokalt: 
```
docker-compose -f docker-compose/docker-compose.yml up
```

- start [backend](https://github.com/navikt/hm-soknad-api) for å starte rapid og evt. populere rapid
- start [hm-soknadsbehandling](https://github.com/navikt/hm-soknadsbehandling) for å lagre søknad i db og sende videre på rapid

- start hm-soknadsbehandling og vent på melding
