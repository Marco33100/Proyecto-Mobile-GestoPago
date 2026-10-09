-- Los usuarios previos pueden existir sin cliente; no se generan contrasenas
-- ni se vinculan registros automaticamente por coincidencia de correo.
ALTER TABLE app_users ADD COLUMN cliente_id BIGINT;
ALTER TABLE app_users ADD CONSTRAINT uk_app_users_cliente UNIQUE (cliente_id);
ALTER TABLE app_users ADD CONSTRAINT fk_app_users_cliente
    FOREIGN KEY (cliente_id) REFERENCES clientes (id) ON DELETE RESTRICT;

-- El identificador de los usuarios nuevos de cliente es su correo completo.
ALTER TABLE app_users ALTER COLUMN identifier TYPE VARCHAR(254);
ALTER TABLE app_users ALTER COLUMN full_name TYPE VARCHAR(254);

ALTER TABLE app_users ADD CONSTRAINT ck_app_users_cliente_email_identifier
    CHECK (cliente_id IS NULL OR (identifier = email AND email = lower(btrim(email))));
