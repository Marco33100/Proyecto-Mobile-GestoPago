-- Esquema inicial consolidado, equivalente al estado final de V1..V16.
-- Exclusivo para una base nueva: usar el perfil new-database.
-- Flyway administra la transaccion y su historial; no insertar versiones manualmente.
-- Los nombres no calificados permiten usar schemas aislados en las pruebas.
-- id_anterior conserva la estructura historica, aunque los IDs nuevos son BIGINT IDENTITY.
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '120s';

CREATE FUNCTION validar_baja_cliente() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF OLD.activo AND NOT NEW.activo AND EXISTS (
        SELECT 1 FROM cuentas WHERE cliente_id = NEW.id AND activa = TRUE
    ) THEN
        RAISE EXCEPTION 'Desactive las cuentas antes de desactivar el cliente'
            USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;

CREATE FUNCTION validar_cuenta_cliente_activo() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
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
$$;

CREATE TABLE app_users (
    id uuid NOT NULL,
    email character varying(254) NOT NULL,
    identifier character varying(254) NOT NULL,
    password_hash character varying(60) NOT NULL,
    full_name character varying(203) NOT NULL,
    enabled boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    cliente_id bigint,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    rol character varying(9) DEFAULT 'CLIENTE'::character varying NOT NULL,
    CONSTRAINT ck_app_users_cliente_email_identifier CHECK (((cliente_id IS NULL) OR (((identifier)::text = (email)::text) AND ((email)::text = lower(btrim((email)::text)))))),
    CONSTRAINT ck_app_users_ejecutivo_sin_cliente CHECK ((((rol)::text <> 'EJECUTIVO'::text) OR (cliente_id IS NULL))),
    CONSTRAINT ck_app_users_rol CHECK (((rol)::text = ANY (ARRAY[('CLIENTE'::character varying)::text, ('EJECUTIVO'::character varying)::text])))
);

CREATE TABLE clientes (
    id_anterior uuid,
    nombre character varying(50) NOT NULL,
    segundo_nombre character varying(50),
    apellido_paterno character varying(50) NOT NULL,
    apellido_materno character varying(50) NOT NULL,
    fecha_nacimiento date NOT NULL,
    curp character varying(18) NOT NULL,
    rfc character varying(13) NOT NULL,
    sexo character varying(10) NOT NULL,
    nacionalidad character varying(60) NOT NULL,
    estado_civil character varying(11) NOT NULL,
    referencia_reconocimiento_facial character varying(255),
    correo character varying(100) NOT NULL,
    telefono_movil character varying(10) NOT NULL,
    telefono_alternativo character varying(10),
    ocupacion character varying(100) NOT NULL,
    empresa character varying(150) NOT NULL,
    ingreso_mensual numeric(19,2) NOT NULL,
    activo boolean DEFAULT true NOT NULL,
    fecha_creacion timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    fecha_actualizacion timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    id bigint NOT NULL,
    CONSTRAINT ck_clientes_correo_normalizado CHECK (((correo)::text = lower(btrim((correo)::text)))),
    CONSTRAINT ck_clientes_curp_formato CHECK (((curp)::text ~ '^[A-Z][AEIOUX][A-Z]{2}[0-9]{2}(0[1-9]|1[0-2])(0[1-9]|[12][0-9]|3[01])[HM][A-Z]{2}[B-DF-HJ-NP-TV-Z]{3}[A-Z0-9][0-9]$'::text)),
    CONSTRAINT ck_clientes_curp_longitud CHECK ((char_length((curp)::text) = 18)),
    CONSTRAINT ck_clientes_curp_mayusculas CHECK (((curp)::text = upper((curp)::text))),
    CONSTRAINT ck_clientes_estado_civil CHECK (((estado_civil)::text = ANY (ARRAY[('SOLTERO'::character varying)::text, ('CASADO'::character varying)::text, ('DIVORCIADO'::character varying)::text, ('VIUDO'::character varying)::text, ('UNION_LIBRE'::character varying)::text]))),
    CONSTRAINT ck_clientes_ingreso CHECK (((ingreso_mensual > (0)::numeric) AND (ingreso_mensual <> ALL (ARRAY['NaN'::numeric, 'Infinity'::numeric, '-Infinity'::numeric])))),
    CONSTRAINT ck_clientes_rfc_formato CHECK (((rfc)::text ~ '^[A-ZÑ&]{3,4}[0-9]{2}(0[1-9]|1[0-2])(0[1-9]|[12][0-9]|3[01])[A-Z0-9]{3}$'::text)),
    CONSTRAINT ck_clientes_rfc_longitud CHECK ((char_length((rfc)::text) = ANY (ARRAY[12, 13]))),
    CONSTRAINT ck_clientes_rfc_mayusculas CHECK (((rfc)::text = upper((rfc)::text))),
    CONSTRAINT ck_clientes_sexo CHECK (((sexo)::text = ANY (ARRAY[('FEMENINO'::character varying)::text, ('MASCULINO'::character varying)::text, ('NO_BINARIO'::character varying)::text]))),
    CONSTRAINT ck_clientes_telefono_alternativo CHECK (((telefono_alternativo IS NULL) OR ((telefono_alternativo)::text ~ '^[0-9]{10}$'::text))),
    CONSTRAINT ck_clientes_telefono_movil CHECK (((telefono_movil)::text ~ '^[0-9]{10}$'::text))
);

ALTER TABLE clientes ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME clientes_id_numerico_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

CREATE TABLE cuentas (
    id uuid NOT NULL,
    numero_cuenta character varying(24) NOT NULL,
    saldo numeric(19,2) NOT NULL,
    activa boolean DEFAULT true NOT NULL,
    fecha_creacion timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    fecha_actualizacion timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    cliente_id bigint NOT NULL,
    CONSTRAINT ck_cuentas_saldo_no_negativo CHECK (((saldo >= (0)::numeric) AND (saldo <> ALL (ARRAY['NaN'::numeric, 'Infinity'::numeric, '-Infinity'::numeric]))))
);

CREATE TABLE domicilios (
    id uuid NOT NULL,
    calle character varying(100) NOT NULL,
    numero_exterior character varying(20) NOT NULL,
    numero_interior character varying(20),
    colonia character varying(100) NOT NULL,
    municipio character varying(100) NOT NULL,
    estado character varying(100) NOT NULL,
    codigo_postal character varying(5) NOT NULL,
    pais character varying(60) NOT NULL,
    fecha_creacion timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    fecha_actualizacion timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    cliente_id bigint NOT NULL,
    CONSTRAINT ck_domicilios_codigo_postal CHECK (((codigo_postal)::text ~ '^[0-9]{5}$'::text))
);

CREATE TABLE gestopago_products (
    product_id integer NOT NULL,
    service_id integer,
    category_service_type_id integer,
    service_name character varying(200),
    product_name character varying(250) NOT NULL,
    front_type integer,
    has_check_digit boolean,
    price numeric(19,4),
    show_help boolean,
    reference_type character varying(20),
    legend text,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT ck_gestopago_products_price_finite CHECK (((price IS NULL) OR (price <> ALL (ARRAY['NaN'::numeric, 'Infinity'::numeric, '-Infinity'::numeric]))))
);

CREATE TABLE gestopago_tokens (
    id integer NOT NULL,
    id_distribuidor integer NOT NULL,
    codigo_dispositivo character varying(100) NOT NULL,
    token text NOT NULL,
    token_type character varying(50),
    expires_in bigint,
    fecha_creacion timestamp without time zone DEFAULT now() NOT NULL,
    fecha_actualizacion timestamp without time zone DEFAULT now() NOT NULL,
    activo boolean DEFAULT true NOT NULL
);

CREATE SEQUENCE gestopago_tokens_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE gestopago_tokens_id_seq OWNED BY gestopago_tokens.id;

CREATE TABLE user_sessions (
    user_id uuid NOT NULL,
    session_id uuid NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);

ALTER TABLE ONLY gestopago_tokens ALTER COLUMN id SET DEFAULT nextval('gestopago_tokens_id_seq'::regclass);

ALTER TABLE ONLY app_users
    ADD CONSTRAINT app_users_pkey PRIMARY KEY (id);

ALTER TABLE ONLY clientes
    ADD CONSTRAINT clientes_pkey PRIMARY KEY (id);

ALTER TABLE ONLY cuentas
    ADD CONSTRAINT cuentas_pkey PRIMARY KEY (id);

ALTER TABLE ONLY domicilios
    ADD CONSTRAINT domicilios_pkey PRIMARY KEY (id);

ALTER TABLE ONLY gestopago_products
    ADD CONSTRAINT gestopago_products_pkey PRIMARY KEY (product_id);

ALTER TABLE ONLY gestopago_tokens
    ADD CONSTRAINT gestopago_tokens_pkey PRIMARY KEY (id);

ALTER TABLE ONLY app_users
    ADD CONSTRAINT uk_app_users_cliente UNIQUE (cliente_id);

ALTER TABLE ONLY app_users
    ADD CONSTRAINT uk_app_users_email UNIQUE (email);

ALTER TABLE ONLY app_users
    ADD CONSTRAINT uk_app_users_identifier UNIQUE (identifier);

ALTER TABLE ONLY clientes
    ADD CONSTRAINT uk_clientes_correo UNIQUE (correo);

ALTER TABLE ONLY clientes
    ADD CONSTRAINT uk_clientes_curp UNIQUE (curp);

ALTER TABLE ONLY clientes
    ADD CONSTRAINT uk_clientes_id_anterior UNIQUE (id_anterior);

ALTER TABLE ONLY clientes
    ADD CONSTRAINT uk_clientes_reconocimiento_facial UNIQUE (referencia_reconocimiento_facial);

ALTER TABLE ONLY clientes
    ADD CONSTRAINT uk_clientes_rfc UNIQUE (rfc);

ALTER TABLE ONLY cuentas
    ADD CONSTRAINT uk_cuentas_numero UNIQUE (numero_cuenta);

ALTER TABLE ONLY domicilios
    ADD CONSTRAINT uk_domicilios_cliente UNIQUE (cliente_id);

ALTER TABLE ONLY gestopago_tokens
    ADD CONSTRAINT uq_gestopago_tokens UNIQUE (id_distribuidor, codigo_dispositivo);

ALTER TABLE ONLY user_sessions
    ADD CONSTRAINT user_sessions_pkey PRIMARY KEY (user_id);

ALTER TABLE ONLY user_sessions
    ADD CONSTRAINT user_sessions_session_id_key UNIQUE (session_id);

CREATE INDEX idx_app_users_ejecutivos_activos ON app_users USING btree (rol) WHERE ((enabled = true) AND ((rol)::text = 'EJECUTIVO'::text));

CREATE INDEX idx_clientes_fecha_id ON clientes USING btree (fecha_creacion DESC, id DESC);

CREATE INDEX idx_clientes_inactivos_fecha_id ON clientes USING btree (fecha_creacion DESC, id DESC) WHERE (activo = false);

CREATE INDEX idx_cuentas_activas_numero ON cuentas USING btree (numero_cuenta) WHERE (activa = true);

CREATE INDEX idx_cuentas_cliente_activa ON cuentas USING btree (cliente_id, activa);

CREATE INDEX idx_gestopago_products_service_id ON gestopago_products USING btree (service_id);

CREATE INDEX idx_user_sessions_expires_at ON user_sessions USING btree (expires_at);

CREATE UNIQUE INDEX uk_app_users_email_normalized ON app_users USING btree (lower(btrim((email)::text)));

CREATE TRIGGER trg_baja_cliente BEFORE UPDATE OF activo ON clientes FOR EACH ROW EXECUTE FUNCTION validar_baja_cliente();

CREATE TRIGGER trg_cuenta_cliente_activo BEFORE INSERT OR UPDATE OF cliente_id, activa ON cuentas FOR EACH ROW EXECUTE FUNCTION validar_cuenta_cliente_activo();

ALTER TABLE ONLY app_users
    ADD CONSTRAINT fk_app_users_cliente FOREIGN KEY (cliente_id) REFERENCES clientes(id) ON DELETE RESTRICT;

ALTER TABLE ONLY cuentas
    ADD CONSTRAINT fk_cuentas_cliente FOREIGN KEY (cliente_id) REFERENCES clientes(id);

ALTER TABLE ONLY domicilios
    ADD CONSTRAINT fk_domicilios_cliente FOREIGN KEY (cliente_id) REFERENCES clientes(id) ON DELETE CASCADE;

ALTER TABLE ONLY user_sessions
    ADD CONSTRAINT fk_user_sessions_user FOREIGN KEY (user_id) REFERENCES app_users(id) ON DELETE CASCADE;
