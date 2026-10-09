-- Ningun usuario existente recibe privilegios de ejecutivo automaticamente.
ALTER TABLE app_users ADD COLUMN rol VARCHAR(20) NOT NULL DEFAULT 'CLIENTE';
ALTER TABLE app_users ADD CONSTRAINT ck_app_users_rol
    CHECK (rol IN ('CLIENTE', 'EJECUTIVO'));
ALTER TABLE app_users ADD CONSTRAINT ck_app_users_ejecutivo_sin_cliente
    CHECK (rol <> 'EJECUTIVO' OR cliente_id IS NULL);
CREATE INDEX idx_app_users_ejecutivos_activos ON app_users (rol) WHERE enabled = TRUE;
