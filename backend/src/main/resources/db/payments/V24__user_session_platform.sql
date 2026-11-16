-- Plataforma de la sesion activa, para avisar antes de reemplazarla desde otro dispositivo.
-- Las sesiones existentes no guardaban este dato; se marcan como MOVIL.
ALTER TABLE user_sessions ADD COLUMN plataforma VARCHAR(5) NOT NULL DEFAULT 'MOVIL';
ALTER TABLE user_sessions ADD CONSTRAINT ck_user_sessions_plataforma CHECK (plataforma IN ('WEB', 'MOVIL'));
