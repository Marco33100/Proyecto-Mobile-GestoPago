-- Ajustes por contrato, no por el maximo observado en la muestra de datos.
-- Los VARCHAR almacenan el contenido real: esto acota entradas, no reserva memoria.
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '120s';

-- Validar ANTES de alterar. Nunca usar LEFT(), SUBSTRING() ni casts que trunquen.
-- El bloqueo evita escrituras concurrentes entre la comprobacion y el cambio.
LOCK TABLE personas, clientes, app_users, accounts IN ACCESS EXCLUSIVE MODE;
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM personas WHERE char_length(nombre) > 50
            OR char_length(apellido_paterno) > 50 OR char_length(apellido_materno) > 50) THEN
        RAISE EXCEPTION 'V15: personas contiene nombres o apellidos de mas de 50 caracteres; revisar sin recortar';
    END IF;
    IF EXISTS (SELECT 1 FROM clientes WHERE char_length(sexo) > 10
            OR char_length(estado_civil) > 11) THEN
        RAISE EXCEPTION 'V15: clientes contiene valores fuera del contrato de sexo o estado civil';
    END IF;
    IF EXISTS (SELECT 1 FROM app_users WHERE char_length(full_name) > 203
            OR char_length(password_hash) > 60 OR char_length(rol) > 9) THEN
        RAISE EXCEPTION 'V15: app_users contiene datos fuera de los limites de nombre completo, BCrypt o rol';
    END IF;
    IF EXISTS (SELECT 1 FROM accounts WHERE char_length(account_number) > 20) THEN
        RAISE EXCEPTION 'V15: accounts contiene numeros fuera del formato historico ACC- y 16 caracteres; revisar sin recortar';
    END IF;
END $$;

ALTER TABLE personas
    ALTER COLUMN nombre TYPE VARCHAR(50),
    ALTER COLUMN apellido_paterno TYPE VARCHAR(50),
    ALTER COLUMN apellido_materno TYPE VARCHAR(50);

-- Longitud del valor mas largo de cada enum, conservando sus CHECK existentes.
ALTER TABLE clientes
    ALTER COLUMN sexo TYPE VARCHAR(10),          -- NO_BINARIO
    ALTER COLUMN estado_civil TYPE VARCHAR(11);  -- UNION_LIBRE

ALTER TABLE app_users
    ALTER COLUMN full_name TYPE VARCHAR(203),    -- 4 partes de 50 + 3 espacios
    ALTER COLUMN password_hash TYPE VARCHAR(60), -- BCryptPasswordEncoder, sin prefijo {id}
    ALTER COLUMN rol TYPE VARCHAR(9);            -- EJECUTIVO

ALTER TABLE accounts
    ALTER COLUMN account_number TYPE VARCHAR(20); -- ACC- + 16 caracteres del generador anterior

-- No reducir correos/identificadores de acceso, domicilios, referencias faciales,
-- tokens ni productos externos por intuicion. Sus contratos tienen limites propios.
-- No alterar PK/FK, identidades, importes, fechas, datos ni estados de activacion.
