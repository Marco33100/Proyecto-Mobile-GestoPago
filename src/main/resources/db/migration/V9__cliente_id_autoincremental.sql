-- Flyway ejecuta esta migración en una transacción.
-- Conserva los datos y convierte las referencias antes de reemplazar la clave.
LOCK TABLE clientes, domicilios, cuentas IN ACCESS EXCLUSIVE MODE;

DROP TRIGGER trg_cuenta_cliente_activo ON cuentas;
DROP TRIGGER trg_baja_cliente ON clientes;

-- PostgreSQL asigna un valor de la identidad también a los clientes existentes.
ALTER TABLE clientes ADD COLUMN id_numerico BIGINT GENERATED ALWAYS AS IDENTITY;
ALTER TABLE domicilios ADD COLUMN cliente_id_numerico BIGINT;
ALTER TABLE cuentas ADD COLUMN cliente_id_numerico BIGINT;

UPDATE domicilios AS d
SET cliente_id_numerico = c.id_numerico
FROM clientes AS c
WHERE d.cliente_id = c.id;

UPDATE cuentas AS a
SET cliente_id_numerico = c.id_numerico
FROM clientes AS c
WHERE a.cliente_id = c.id;

ALTER TABLE domicilios ALTER COLUMN cliente_id_numerico SET NOT NULL;
ALTER TABLE cuentas ALTER COLUMN cliente_id_numerico SET NOT NULL;

ALTER TABLE domicilios DROP CONSTRAINT fk_domicilios_cliente;
ALTER TABLE cuentas DROP CONSTRAINT fk_cuentas_cliente;
ALTER TABLE clientes DROP CONSTRAINT clientes_pkey;

-- Se conserva el UUID previo como referencia histórica, sin exigirlo en nuevos registros.
ALTER TABLE clientes RENAME COLUMN id TO id_anterior;
ALTER TABLE clientes ALTER COLUMN id_anterior DROP NOT NULL;
ALTER TABLE clientes ADD CONSTRAINT uk_clientes_id_anterior UNIQUE (id_anterior);
ALTER TABLE clientes RENAME COLUMN id_numerico TO id;
ALTER TABLE clientes ADD CONSTRAINT clientes_pkey PRIMARY KEY (id);

-- Al retirar las columnas anteriores se retiran también sus índices dependientes.
ALTER TABLE domicilios DROP COLUMN cliente_id;
ALTER TABLE domicilios RENAME COLUMN cliente_id_numerico TO cliente_id;
ALTER TABLE domicilios ADD CONSTRAINT uk_domicilios_cliente UNIQUE (cliente_id);
ALTER TABLE domicilios ADD CONSTRAINT fk_domicilios_cliente
    FOREIGN KEY (cliente_id) REFERENCES clientes (id) ON DELETE CASCADE;

ALTER TABLE cuentas DROP COLUMN cliente_id;
ALTER TABLE cuentas RENAME COLUMN cliente_id_numerico TO cliente_id;
ALTER TABLE cuentas ADD CONSTRAINT fk_cuentas_cliente
    FOREIGN KEY (cliente_id) REFERENCES clientes (id);
CREATE INDEX idx_cuentas_cliente ON cuentas (cliente_id);
CREATE INDEX idx_cuentas_cliente_activa ON cuentas (cliente_id, activa);

CREATE TRIGGER trg_cuenta_cliente_activo
BEFORE INSERT OR UPDATE OF cliente_id, activa ON cuentas
FOR EACH ROW EXECUTE FUNCTION validar_cuenta_cliente_activo();

CREATE TRIGGER trg_baja_cliente
BEFORE UPDATE OF activo ON clientes
FOR EACH ROW EXECUTE FUNCTION validar_baja_cliente();
