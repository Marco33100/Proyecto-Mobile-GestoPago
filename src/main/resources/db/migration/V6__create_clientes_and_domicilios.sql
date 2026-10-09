CREATE TABLE clientes (
    id UUID PRIMARY KEY,
    nombre VARCHAR(50) NOT NULL,
    segundo_nombre VARCHAR(50),
    apellido_paterno VARCHAR(50) NOT NULL,
    apellido_materno VARCHAR(50) NOT NULL,
    fecha_nacimiento DATE NOT NULL,
    curp VARCHAR(18) NOT NULL,
    rfc VARCHAR(13) NOT NULL,
    sexo VARCHAR(20) NOT NULL,
    nacionalidad VARCHAR(60) NOT NULL,
    estado_civil VARCHAR(30) NOT NULL,
    referencia_reconocimiento_facial VARCHAR(255),
    correo VARCHAR(100) NOT NULL,
    telefono_movil VARCHAR(10) NOT NULL,
    telefono_alternativo VARCHAR(10),
    ocupacion VARCHAR(100) NOT NULL,
    empresa VARCHAR(150) NOT NULL,
    ingreso_mensual NUMERIC(19, 2) NOT NULL,
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    fecha_creacion TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_actualizacion TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_clientes_curp UNIQUE (curp),
    CONSTRAINT uk_clientes_rfc UNIQUE (rfc),
    CONSTRAINT uk_clientes_correo UNIQUE (correo),
    CONSTRAINT uk_clientes_reconocimiento_facial UNIQUE (referencia_reconocimiento_facial),
    CONSTRAINT ck_clientes_curp_longitud CHECK (char_length(curp) = 18),
    CONSTRAINT ck_clientes_curp_mayusculas CHECK (curp = UPPER(curp)),
    CONSTRAINT ck_clientes_rfc_longitud CHECK (char_length(rfc) IN (12, 13)),
    CONSTRAINT ck_clientes_rfc_mayusculas CHECK (rfc = UPPER(rfc)),
    CONSTRAINT ck_clientes_correo_normalizado CHECK (correo = LOWER(BTRIM(correo))),
    CONSTRAINT ck_clientes_telefono_movil CHECK (telefono_movil ~ '^[0-9]{10}$'),
    CONSTRAINT ck_clientes_telefono_alternativo CHECK (
        telefono_alternativo IS NULL OR telefono_alternativo ~ '^[0-9]{10}$'
    ),
    CONSTRAINT ck_clientes_ingreso CHECK (ingreso_mensual > 0),
    CONSTRAINT ck_clientes_sexo CHECK (sexo IN ('FEMENINO', 'MASCULINO', 'NO_BINARIO')),
    CONSTRAINT ck_clientes_estado_civil CHECK (
        estado_civil IN ('SOLTERO', 'CASADO', 'DIVORCIADO', 'VIUDO', 'UNION_LIBRE')
    )
);

CREATE INDEX idx_clientes_activos ON clientes (activo) WHERE activo = TRUE;
CREATE INDEX idx_clientes_fecha_creacion ON clientes (fecha_creacion);
CREATE INDEX idx_clientes_apellidos ON clientes (apellido_paterno, apellido_materno);

CREATE TABLE domicilios (
    id UUID PRIMARY KEY,
    cliente_id UUID NOT NULL,
    calle VARCHAR(100) NOT NULL,
    numero_exterior VARCHAR(20) NOT NULL,
    numero_interior VARCHAR(20),
    colonia VARCHAR(100) NOT NULL,
    municipio VARCHAR(100) NOT NULL,
    estado VARCHAR(100) NOT NULL,
    codigo_postal VARCHAR(5) NOT NULL,
    pais VARCHAR(60) NOT NULL,
    fecha_creacion TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_actualizacion TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_domicilios_cliente UNIQUE (cliente_id),
    CONSTRAINT ck_domicilios_codigo_postal CHECK (codigo_postal ~ '^[0-9]{5}$'),
    CONSTRAINT fk_domicilios_cliente
        FOREIGN KEY (cliente_id) REFERENCES clientes (id) ON DELETE CASCADE
);

CREATE INDEX idx_domicilios_codigo_postal ON domicilios (codigo_postal);
