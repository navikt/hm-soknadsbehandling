ALTER TABLE V1_SOKNAD ADD COLUMN ER_DIGITAL VARCHAR(10);
UPDATE V1_SOKNAD SET ER_DIGITAL=TRUE where TYPE IS NULL;
ALTER TABLE V1_SOKNAD ALTER COLUMN FNRINNSENDER DROP NOT NULL;