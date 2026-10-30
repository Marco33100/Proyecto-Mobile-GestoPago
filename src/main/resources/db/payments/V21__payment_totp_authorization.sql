CREATE TABLE payment_second_factors (
    usuario_id uuid PRIMARY KEY REFERENCES app_users(id),
    secreto_cifrado text, huella varchar(64), ultimo_paso bigint NOT NULL DEFAULT -1,
    pendiente_cifrado text, sesion_registro uuid, vence_en timestamp with time zone,
    intentos integer NOT NULL DEFAULT 0 CHECK (intentos >= 0),
    bloqueado_hasta timestamp with time zone, recuperacion_hashes text,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT ck_payment_totp_active CHECK ((secreto_cifrado IS NULL) = (huella IS NULL))
);
-- Solo las preparaciones anteriores dependian de firmas biometricas. Nunca tocar operaciones enviadas ni retenciones.
UPDATE payment_orders SET estado = 'EXPIRADO', version = version + 1 WHERE estado = 'PREPARADO';
-- Conservar payment_biometric_keys como historial de la migracion anterior; ya no es un mecanismo de autorizacion.
