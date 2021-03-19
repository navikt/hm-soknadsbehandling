package no.nav.hjelpemidler.soknad.mottak

import com.fasterxml.jackson.databind.ObjectMapper
import no.nav.hjelpemidler.soknad.mottak.service.SoknadData
import no.nav.hjelpemidler.soknad.mottak.service.Status
import java.util.UUID

internal fun mockSøknad(id: UUID) = SoknadData(
    "15084300133",
    "12345678910",
    id,
    ObjectMapper().readTree(
        """ {
                          "fnrBruker": "15084300133",
                          "soknadId": "62f68547-11ae-418c-8ab7-4d2af985bcd9",
                          "datoOpprettet": "2021-02-23T09:46:45.146+00:00",
                          "soknad": {
                              "id": "62f68547-11ae-418c-8ab7-4d2af985bcd9",
                              "date": "2020-06-19",
                              "bruker": {
                                "fnummer": "15084300133",
                                "fornavn": "fornavn",
                                "signatur": "FULLMAKT",
                                "etternavn": "etternavn",
                                "telefonNummer": "12345678",
                                "poststed": "Stedet"
                              },
                              "brukersituasjon": {
                                "bostedRadioButton": "Hjemme",
                                "bruksarenaErDagliglivet": true,
                                "nedsattFunksjonTypes": {
                                    "bevegelse": true,
                                    "kognisjon": false,
                                    "horsel": true
                                }
                              }, 
                              "hjelpemidler": {
                            "hjelpemiddelTotaltAntall": 2,
                            "hjelpemiddelListe": [
                              {
                                "uniqueKey": "1234561592555082660",
                                "hmsNr": "123456",
                                "beskrivelse": "beskrivelse",
                                "begrunnelsen": "begrunnelse",
                                "antall": 1,
                                "navn": "Hjelpemiddelnavn",
                                "utlevertFraHjelpemiddelsentralen": true,
                                "tilleggsinformasjon": "Tilleggsinformasjon",
                                "kanIkkeTilsvarande": true,
                                "hjelpemiddelkategori": "Arbeidsstoler",
                                "produkt": {
                                  "postrank": "1"
                                },
                                "vilkarliste": [
                                  {
                                    "id": 1,
                                    "vilkartekst": "Vilkår 1",
                                    "tilleggsinfo": "Tilleggsinfo",
                                    "checked": true
                                  }
                                ],
                                "tilbehorListe": [
                                  {
                                    "hmsnr": "654321",
                                    "navn": "Tilbehør 1",
                                    "antall": 1
                                  }
                                ]
                              }
                            ]
                          },
                              "levering": {
                                 "hmfFornavn": "formidlerFornavn",
                                 "hmfEtternavn": "formidlerEtternavn", 
                                 "hmfArbeidssted": "arbeidssted",
                                  "hmfStilling": "stilling",
                                  "hmfPostadresse": "postadresse arbeidssted",
                                  "hmfPostnr": "1234",
                                  "hmfPoststed": "poststed",
                                  "hmfTelefon": "12345678",
                                  "hmfTreffesEnklest": "treffedager",
                                  "hmfEpost": "epost@adad.com",
                                   "opfRadioButton": "Hjelpemiddelformidler",
                                   "utleveringsmaateRadioButton": "FolkeregistrertAdresse",
                                   "utleveringskontaktpersonRadioButton": "Hjelpemiddelbruker"
                              }
                          }
                        } """
    ),
    status = Status.VENTER_GODKJENNING,
    kommunenavn = null
)
