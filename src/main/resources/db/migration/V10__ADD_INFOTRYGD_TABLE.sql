CREATE TABLE IF NOT EXISTS V1_INFOTRYGD_DATA
(
    SOKNADS_ID       UUID                     NOT NULL,
    FNR_BRUKER       CHAR(11)                 NOT NULL,
    TRYGDEKONTORNR   CHAR(4)                  NOT NULL,
    SAKSBLOKK        CHAR(1)                  NOT NULL,
    SAKSNR           CHAR(2)                  NOT NULL,
    RESULTAT         VARCHAR(10)              NULL,
    VEDTAKSDATO      DATE                     NULL,
    CREATED          TIMESTAMP                NOT NULL default (now()),
    PRIMARY KEY (SOKNADS_ID)
);

CREATE INDEX V1_INFOTRYGD_DATA_FNR_BRUKER_IDX ON V1_INFOTRYGD_DATA (FNR_BRUKER);
CREATE UNIQUE INDEX V1_INFOTRYGD_DATA_ORDRE_IDX ON V1_INFOTRYGD_DATA (FNR_BRUKER, SAKSBLOKK, SAKSNR, VEDTAKSDATO);