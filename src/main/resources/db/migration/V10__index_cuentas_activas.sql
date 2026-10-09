-- Facilita el listado paginado de cuentas activas ordenadas por numero.
CREATE INDEX idx_cuentas_activas_numero ON cuentas (numero_cuenta) WHERE activa = TRUE;
