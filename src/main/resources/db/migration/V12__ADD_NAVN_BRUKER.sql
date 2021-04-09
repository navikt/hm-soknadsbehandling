ALTER TABLE V1_SOKNAD ADD COLUMN NAVN_BRUKER VARCHAR(100);

UPDATE V1_SOKNAD q1
SET NAVN_BRUKER = q2.navn
    FROM (select
            concat(
            data -> 'soknad' -> 'bruker' ->> 'fornavn',
            ' ',
            data -> 'soknad' -> 'bruker' ->> 'etternavn'
            ) as navn,
            soknads_id
          from v1_soknad
         ) AS q2
WHERE q1.soknads_id = q2.soknads_id
AND NOT EXISTS(
    SELECT 1 FROM V1_STATUS st
    WHERE  st.soknads_id = q1.soknads_id
    AND st.status in ('SLETTET', 'UTLØPT')
    );
