-- Retirar modelos sustituidos por clientes/cuentas sin borrar datos historicos.
-- No modificar V1..V15: sus checksums deben seguir siendo validos.
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '120s';

LOCK TABLE personas, accounts IN ACCESS EXCLUSIVE MODE;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM personas) THEN
        ALTER TABLE personas RENAME TO legacy_personas;
    ELSE
        DROP TABLE personas;
    END IF;

    IF EXISTS (SELECT 1 FROM accounts) THEN
        ALTER TABLE accounts RENAME TO legacy_accounts;
    ELSE
        DROP TABLE accounts;
    END IF;
END $$;

-- Sin CASCADE: una dependencia inesperada debe abortar, no desaparecer.
-- Los archivos legacy_* no se mapean en JPA ni se exponen mediante endpoints.
