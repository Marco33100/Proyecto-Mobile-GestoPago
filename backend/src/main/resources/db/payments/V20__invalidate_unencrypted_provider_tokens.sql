-- No conservar tokens historicos en texto plano. Reautenticar una vez bajo el bloqueo distribuido.
-- No se modifican cuentas, saldos ni operaciones financieras.
UPDATE gestopago_tokens
SET token = 'REAUTHENTICATION_REQUIRED', activo = false
WHERE token NOT LIKE 'gp-token:v1:%';

ALTER TABLE gestopago_tokens ADD CONSTRAINT ck_gestopago_active_token_encrypted
    CHECK (NOT activo OR token LIKE 'gp-token:v1:%');
