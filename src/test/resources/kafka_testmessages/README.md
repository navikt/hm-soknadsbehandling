Json fila i denne mappe er en dump av meldinger som produseres av OEBS listener når ordrelinjer "leveres" fra OEBS. 
For å teste i dev uten å måtte være avhengig av hele OEBS verdikjeden, kan disse meldingene postes rett på kafka. 

For at det skal funke, må det være opprettet en søknad som er rutet til HOTSAK og innvilget. 
Deretter må saksnummer og søknadID oppdateres slik at de stemmer med søknadID på søknaden du opprettet og HOTSAK saksnummer før melding publiseres på kafka. 
Publisering på kafka kan for eksempel gjøres med Kafkacat