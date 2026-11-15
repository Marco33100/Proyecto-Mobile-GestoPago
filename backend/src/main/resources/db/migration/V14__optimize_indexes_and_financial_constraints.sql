-- Migracion transaccional: no altera identificadores ni elimina registros.
-- Fallar pronto si la aplicacion mantiene un bloqueo; no esperar indefinidamente.
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '120s';

-- NUMERIC permite NaN: las comparaciones > 0 y >= 0 no lo rechazan.
-- Validar los datos existentes; si hay un importe invalido, revertir toda V14.
ALTER TABLE clientes DROP CONSTRAINT ck_clientes_ingreso;
ALTER TABLE clientes ADD CONSTRAINT ck_clientes_ingreso CHECK (
    ingreso_mensual > 0
    AND ingreso_mensual NOT IN ('NaN'::numeric, 'Infinity'::numeric, '-Infinity'::numeric)
);

ALTER TABLE cuentas DROP CONSTRAINT ck_cuentas_saldo_no_negativo;
ALTER TABLE cuentas ADD CONSTRAINT ck_cuentas_saldo_no_negativo CHECK (
    saldo >= 0
    AND saldo NOT IN ('NaN'::numeric, 'Infinity'::numeric, '-Infinity'::numeric)
);

-- El precio externo conserva su nulabilidad y sus reglas de signo existentes.
ALTER TABLE gestopago_products ADD CONSTRAINT ck_gestopago_products_price_finite CHECK (
    price IS NULL OR price NOT IN ('NaN'::numeric, 'Infinity'::numeric, '-Infinity'::numeric)
);

-- UNIQUE(email) ya crea un B-tree equivalente; conservar la restriccion.
DROP INDEX idx_app_users_email;

-- El indice compuesto atiende consultas por cliente y por cliente/cuenta activa,
-- incluido el control del FK y los triggers de baja logica.
DROP INDEX idx_cuentas_cliente;

-- Orden real de la paginacion y consultas por rango de fechas.
CREATE INDEX idx_clientes_fecha_id ON clientes (fecha_creacion DESC, id DESC);
DROP INDEX idx_clientes_fecha_creacion;
DROP INDEX idx_clientes_activos;

-- Los clientes se crean activos: evitar otro indice casi completo para ese valor.
-- Las bajas, normalmente minoritarias, tienen un indice parcial con el mismo orden.
CREATE INDEX idx_clientes_inactivos_fecha_id
    ON clientes (fecha_creacion DESC, id DESC) WHERE activo = FALSE;

-- No hay busquedas por apellidos o CP en los repositorios actuales.
-- Si se incorpora esa funcionalidad, evaluar nuevos indices para sus consultas.
DROP INDEX idx_clientes_apellidos;
DROP INDEX idx_domicilios_codigo_postal;

-- El bootstrap solo busca ejecutivos activos, no todos los clientes activos.
DROP INDEX idx_app_users_ejecutivos_activos;
CREATE INDEX idx_app_users_ejecutivos_activos ON app_users (rol)
    WHERE enabled = TRUE AND rol = 'EJECUTIVO';

-- No modificar UK de CURP/RFC/correos/cuenta, FKs, UUID historicos ni hashes.
