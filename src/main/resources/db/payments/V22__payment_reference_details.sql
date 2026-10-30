-- Preserve the provider debt snapshot encrypted; existing sent payments remain unchanged.
ALTER TABLE payment_orders ADD COLUMN consulta_referencia_cifrada text;
ALTER TABLE payment_orders ADD COLUMN consulta_referencia_en timestamp with time zone;
ALTER TABLE payment_orders ADD COLUMN tipo_front integer;
-- Old preparations lack a verifiable debt snapshot. Expire only those requiring verifyReference.
UPDATE payment_orders SET estado = 'EXPIRADO', version = version + 1
WHERE estado = 'PREPARADO' AND NOT referencia_validada;
