-- Old encrypted orders cannot be backfilled with an operation fingerprint in SQL.
-- Legacy open orders fail closed until resolved; prepared ones must be prepared again.
ALTER TABLE payment_orders ADD COLUMN huella_operacion varchar(64);
ALTER TABLE payment_orders ADD COLUMN huella_catalogo varchar(64);
ALTER TABLE payment_orders ADD COLUMN duplicado_proveedor boolean NOT NULL DEFAULT false;
ALTER TABLE payment_orders ADD COLUMN id_transaccion_previa varchar(60);
ALTER TABLE payment_orders ADD COLUMN autorizacion_previa varchar(60);
-- Preserve a known legacy duplicate as unresolved, never reinterpret its later confirmation as a new debit.
UPDATE payment_orders SET duplicado_proveedor = true
    WHERE codigo_proveedor = '06' AND estado IN ('ENVIADO', 'PENDIENTE', 'REVISION');
CREATE INDEX ix_payment_operation ON payment_orders(usuario_id, huella_operacion, estado);
CREATE UNIQUE INDEX ux_payment_open_operation ON payment_orders(usuario_id, huella_operacion)
    WHERE estado IN ('ENVIADO', 'PENDIENTE', 'REVISION');
