-- Impide activar una cuenta si el cliente no está activo, incluso fuera de la API.
CREATE OR REPLACE FUNCTION validar_cuenta_cliente_activo()
RETURNS trigger AS $$
BEGIN
    IF NEW.activa THEN
        PERFORM 1 FROM clientes
        WHERE id = NEW.cliente_id AND activo = TRUE
        FOR SHARE;
        IF NOT FOUND THEN
            RAISE EXCEPTION 'Una cuenta activa requiere un cliente activo'
                USING ERRCODE = '23514';
        END IF;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_cuenta_cliente_activo
BEFORE INSERT OR UPDATE OF cliente_id, activa ON cuentas
FOR EACH ROW EXECUTE FUNCTION validar_cuenta_cliente_activo();

-- La aplicación desactiva primero las cuentas y después el cliente.
CREATE OR REPLACE FUNCTION validar_baja_cliente()
RETURNS trigger AS $$
BEGIN
    IF OLD.activo AND NOT NEW.activo AND EXISTS (
        SELECT 1 FROM cuentas WHERE cliente_id = NEW.id AND activa = TRUE
    ) THEN
        RAISE EXCEPTION 'Desactive las cuentas antes de desactivar el cliente'
            USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_baja_cliente
BEFORE UPDATE OF activo ON clientes
FOR EACH ROW EXECUTE FUNCTION validar_baja_cliente();

ALTER TABLE clientes
    ADD CONSTRAINT ck_clientes_curp_formato CHECK (
        curp ~ '^[A-Z][AEIOUX][A-Z]{2}[0-9]{2}(0[1-9]|1[0-2])(0[1-9]|[12][0-9]|3[01])[HM][A-Z]{2}[B-DF-HJ-NP-TV-Z]{3}[A-Z0-9][0-9]$'
    ),
    ADD CONSTRAINT ck_clientes_rfc_formato CHECK (
        rfc ~ '^[A-ZÑ&]{3,4}[0-9]{2}(0[1-9]|1[0-2])(0[1-9]|[12][0-9]|3[01])[A-Z0-9]{3}$'
    );
