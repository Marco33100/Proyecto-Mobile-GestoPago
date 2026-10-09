CREATE TABLE cuentas (
    id UUID PRIMARY KEY,
    cliente_id UUID NOT NULL,
    numero_cuenta VARCHAR(24) NOT NULL,
    saldo NUMERIC(19, 2) NOT NULL,
    activa BOOLEAN NOT NULL DEFAULT TRUE,
    fecha_creacion TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_actualizacion TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_cuentas_numero UNIQUE (numero_cuenta),
    CONSTRAINT ck_cuentas_saldo_no_negativo CHECK (saldo >= 0),
    CONSTRAINT fk_cuentas_cliente
        FOREIGN KEY (cliente_id) REFERENCES clientes (id)
);

CREATE INDEX idx_cuentas_cliente ON cuentas (cliente_id);
CREATE INDEX idx_cuentas_cliente_activa ON cuentas (cliente_id, activa);
