CREATE TABLE IF NOT EXISTS V1_OEBS_DATA
(
    SOKNADS_ID          UUID                     NOT NULL,
    FNR_BRUKER          CHAR(11)                 NOT NULL,
    SERVICEFORESPOERSEL BIGINT                   NULL,
    ORDRENR             INTEGER                  NOT NULL,
    ORDRELINJE          INTEGER                  NOT NULL,
    DELORDRELINJE       INTEGER                  NOT NULL,
    ARTIKKELNR          CHAR(6)                  NOT NULL,
    ANTALL              INTEGER                  NOT NULL,
    PRODUKTGRUPPE       TEXT                     NOT NULL,
    DATA                JSONB                    NOT NULL,
    CREATED             TIMESTAMP                NOT NULL default (now()),
    PRIMARY KEY (SOKNADS_ID, ORDRENR, ORDRELINJE, DELORDRELINJE)
);

CREATE INDEX V1_OEBS_DATA_FNR_BRUKER_ORDRE_IDX ON V1_OEBS_DATA (FNR_BRUKER);
