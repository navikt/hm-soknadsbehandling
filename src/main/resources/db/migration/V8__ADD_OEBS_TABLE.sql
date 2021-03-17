CREATE TABLE IF NOT EXISTS V1_OEBS_DATA
(
    SOKNADS_ID          UUID                     NOT NULL,
    FNR_BRUKER          CHAR(11)                 NOT NULL,
    SERVICEFORESPOERSEL VARCHAR(11)                      ,
    ORDRENR             INTEGER                  NOT NULL,
    ORDRELINJE          INTEGER                  NOT NULL,
    VEDTAKSDATO         TIMESTAMP                        ,
    ARTIKKELNR          CHAR(6)                  NOT NULL,
    ANTALL              INTEGER                  NOT NULL,
    DATA                JSONB                    NOT NULL,
    created             TIMESTAMP                NOT NULL default (now()),
    PRIMARY KEY (FNR_BRUKER, SOKNADS_ID, ORDRENR, ORDRELINJE, VEDTAKSDATO)
    );

CREATE INDEX V1_OEBS_DATA_FNR_BRUKER_ORDRE_IDX ON V1_OEBS_DATA (FNR_BRUKER);
CREATE INDEX V1_OEBS_DATA_ORDRE_IDX ON V1_OEBS_DATA (SOKNADS_ID, FNR_BRUKER, ORDRENR, ORDRELINJE, VEDTAKSDATO);
