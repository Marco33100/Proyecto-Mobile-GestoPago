ALTER TABLE payment_orders ADD COLUMN intento_confirmacion uuid;
ALTER TABLE payment_orders ADD COLUMN numero_confirmaciones integer NOT NULL DEFAULT 0;
ALTER TABLE payment_orders ADD COLUMN motivo_revision varchar(64);
ALTER TABLE payment_orders ADD CONSTRAINT ck_payment_confirmation_count CHECK (numero_confirmaciones >= 0);
UPDATE payment_orders SET motivo_revision = 'REVISION_ANTERIOR' WHERE estado = 'REVISION';
