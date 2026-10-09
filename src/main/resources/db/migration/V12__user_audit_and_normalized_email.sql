ALTER TABLE app_users ADD COLUMN updated_at TIMESTAMPTZ;
UPDATE app_users SET updated_at = created_at;
ALTER TABLE app_users ALTER COLUMN updated_at SET DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE app_users ALTER COLUMN updated_at SET NOT NULL;
ALTER TABLE app_users ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

-- Impide duplicados por diferencias de mayusculas o espacios, incluso via SQL.
-- Si existieran duplicados historicos, se detiene la migracion sin borrar datos.
CREATE UNIQUE INDEX uk_app_users_email_normalized ON app_users (lower(btrim(email)));
