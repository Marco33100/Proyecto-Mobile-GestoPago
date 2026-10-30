ALTER TABLE payment_orders ADD COLUMN huella_reglas varchar(64);
ALTER TABLE payment_orders ADD COLUMN reglas_cifradas text;
-- Preparaciones anteriores no contienen una copia verificable de sus reglas. No tocar envios ni retenciones.
UPDATE payment_orders SET estado='EXPIRADO',version=version+1 WHERE estado='PREPARADO';

CREATE INDEX ix_payment_budget ON payment_orders(usuario_id,enviado_en,estado);
CREATE TABLE payment_reconciliations (
    id uuid PRIMARY KEY,
    pago_id uuid NOT NULL REFERENCES payment_orders(id),
    version_pago bigint NOT NULL CHECK (version_pago>=0),
    propuesto_por uuid NOT NULL REFERENCES app_users(id),
    decidido_por uuid REFERENCES app_users(id),
    estado varchar(12) NOT NULL CHECK (estado IN ('PROPUESTA','APROBADA','RECHAZADA')),
    contenido_cifrado text NOT NULL,
    creado_en timestamp with time zone NOT NULL,
    vence_en timestamp with time zone NOT NULL,
    decidido_en timestamp with time zone,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT ck_reconciliation_decision CHECK (
        (estado='PROPUESTA' AND decidido_por IS NULL AND decidido_en IS NULL)
        OR (estado IN ('APROBADA','RECHAZADA') AND decidido_por IS NOT NULL AND decidido_en IS NOT NULL)),
    CONSTRAINT ck_reconciliation_approver CHECK (estado<>'APROBADA' OR decidido_por<>propuesto_por),
    CONSTRAINT ck_reconciliation_expiry CHECK (vence_en>creado_en)
);
CREATE UNIQUE INDEX ux_payment_open_reconciliation ON payment_reconciliations(pago_id) WHERE estado='PROPUESTA';
CREATE INDEX ix_payment_reconciliation_history ON payment_reconciliations(pago_id,creado_en);
