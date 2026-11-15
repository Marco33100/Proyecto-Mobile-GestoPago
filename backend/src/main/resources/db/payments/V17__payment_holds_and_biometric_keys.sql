-- Additive migration shared by both db/initial (Render) and db/migration (legacy).
ALTER TABLE cuentas ADD COLUMN saldo_retenido numeric(19,2) NOT NULL DEFAULT 0;
ALTER TABLE cuentas ADD CONSTRAINT ck_cuentas_retencion
    CHECK (saldo_retenido >= 0 AND saldo_retenido <= saldo AND saldo_retenido NOT IN ('NaN'::numeric, 'Infinity'::numeric, '-Infinity'::numeric));

CREATE TABLE payment_biometric_keys (
    usuario_id uuid PRIMARY KEY REFERENCES app_users(id),
    clave_publica varchar(256), huella varchar(64), reto_registro varchar(600),
    vence_en timestamp with time zone, intentos integer NOT NULL DEFAULT 0,
    version bigint NOT NULL DEFAULT 0
);
CREATE TABLE payment_orders (
    id uuid PRIMARY KEY,
    usuario_id uuid NOT NULL REFERENCES app_users(id),
    cuenta_id uuid NOT NULL REFERENCES cuentas(id),
    numero_cuenta varchar(24) NOT NULL,
    huella varchar(64) NOT NULL, huella_clave varchar(64) NOT NULL,
    upc varchar(10) NOT NULL UNIQUE,
    concepto varchar(250) NOT NULL, monto numeric(19,2) NOT NULL CHECK (monto > 0 AND monto NOT IN ('NaN'::numeric, 'Infinity'::numeric, '-Infinity'::numeric)),
    payload_cifrado text NOT NULL, reto varchar(1500) NOT NULL,
    estado varchar(16) NOT NULL CHECK (estado IN ('PREPARADO','ENVIADO','PENDIENTE','APLICADO','RECHAZADO','EXPIRADO','REVISION')),
    creado_en timestamp with time zone NOT NULL, vence_en timestamp with time zone NOT NULL,
    enviado_en timestamp with time zone, proxima_confirmacion timestamp with time zone,
    codigo_proveedor varchar(40), id_transaccion varchar(60), autorizacion varchar(60),
    recibo_cifrado text, referencia_validada boolean NOT NULL DEFAULT false, requiere_pin boolean NOT NULL DEFAULT false,
    version bigint NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX ux_payment_provider_tx ON payment_orders(id_transaccion) WHERE id_transaccion IS NOT NULL;
CREATE INDEX ix_payment_due ON payment_orders(estado, proxima_confirmacion);
CREATE INDEX ix_payment_owner ON payment_orders(usuario_id, creado_en DESC);
CREATE TABLE payment_evidence (
    id uuid PRIMARY KEY, pago_id uuid NOT NULL REFERENCES payment_orders(id),
    metodo varchar(16) NOT NULL, contenido text NOT NULL, creado_en timestamp with time zone NOT NULL
);
CREATE INDEX ix_payment_evidence_order ON payment_evidence(pago_id, creado_en);
