# README




### Tilgang til Postgres databasen

For utfyllende dokumentasjon se [Postgres i NAV](https://github.com/navikt/utvikling/blob/master/PostgreSQL.md)

#### Tldr

Applikasjonen benytter seg av dynamisk genererte bruker/passord til database.
For å koble seg til databasen må man genere bruker/passord(som varer i en time)
på følgende måte:

Installere [Vault](https://www.vaultproject.io/downloads.html)

Generere bruker/passord: 

```

export VAULT_ADDR=https://vault.adeo.no USER=NAV_IDENT
vault login -method=oidc


```

Hvis du får noe à la `connection refused`, må du før `vault login -method=oidc` legge til:
```
export HTTPS_PROXY="socks5://localhost:14122" 
export NO_PROXY=".microsoftonline.com,.terraform.io,.hashicorp.com"
```

Preprod credentials:

```
vault read postgresql/preprod-fss/creds/dp-soknad-admin

```

Prod credentials:

```
vault read postgresql/prod-fss/creds/dp-soknad-admin

```

Bruker/passord kombinasjonen kan brukes til å koble seg til de aktuelle databasene(Fra utvikler image...)
F.eks

```

psql -d $DATABASE_NAME -h $DATABASE_HOST -U $GENERERT_BRUKER_NAVN

```