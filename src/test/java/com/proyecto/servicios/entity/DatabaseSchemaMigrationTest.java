package com.proyecto.servicios.entity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.proyecto.servicios.entity.sf.*;
import com.proyecto.servicios.entity.gestopago.ProductEntity;
import com.proyecto.servicios.entity.gestopago.GestoPagoToken;
import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;
import java.util.List;
import java.util.Locale;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;

/** Pruebas de DDL y planes sobre schemas desechables, nunca sobre public. */
@EnabledIfEnvironmentVariable(named = "ONBOARDING_TEST_JDBC_URL", matches = ".+")
class DatabaseSchemaMigrationTest {
    private String url;
    private String username;
    private String password;
    private String schema;
    private Connection connection;

    @BeforeEach
    void setUp() throws Exception {
        url = System.getenv("ONBOARDING_TEST_JDBC_URL");
        username = System.getenv().getOrDefault("ONBOARDING_TEST_DB_USER", "onboarding_test");
        password = System.getenv().getOrDefault("ONBOARDING_TEST_DB_PASSWORD", "");
        schema = "schema_test_" + UUID.randomUUID().toString().replace("-", "");
        connection = DriverManager.getConnection(url, username, password);
        sql("CREATE SCHEMA " + schema);
        sql("SET search_path TO " + schema);
        sql("SET statement_timeout = '120s'");
    }

    @AfterEach
    void tearDown() throws Exception {
        if (connection != null) {
            try {
                if (!schema.matches("schema_test_[a-f0-9]{32}")) throw new IllegalStateException("Schema invalido");
                sql("DROP SCHEMA " + schema + " CASCADE");
            } finally {
                connection.close();
            }
        }
    }

    private Flyway flyway(String target) {
        return Flyway.configure().dataSource(url, username, password)
                .schemas(schema).defaultSchema(schema).target(target).load();
    }

    private Flyway initialFlyway() {
        return Flyway.configure().dataSource(url, username, password)
                .schemas(schema).defaultSchema(schema).locations("classpath:db/initial")
                .baselineOnMigrate(false).load();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void tiposLongitudesNulabilidadYPrecisionDeEntidadesCoincidenConPostgres(boolean consolidado) throws Exception {
        (consolidado ? initialFlyway() : flyway("16")).migrate();
        var entities = List.of(ClienteEntity.class, DomicilioEntity.class, CuentaEntity.class,
                UserEntity.class, UserSessionEntity.class,
                ProductEntity.class, GestoPagoToken.class);
        int checked = 0;
        for (Class<?> entity : entities) {
            String table = entity.getAnnotation(Table.class).name();
            for (var field : entity.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) continue;
                Column column = field.getAnnotation(Column.class);
                JoinColumn join = field.getAnnotation(JoinColumn.class);
                String name = join != null ? join.name()
                        : column != null && !column.name().isEmpty() ? column.name()
                        : field.getName().toLowerCase(Locale.ROOT);
                Class<?> type = field.getType();
                if (join != null) {
                    type = java.util.Arrays.stream(type.getDeclaredFields())
                            .filter(id -> id.isAnnotationPresent(Id.class)).findFirst().orElseThrow().getType();
                }
                String label = table + "." + name;
                try (var statement = connection.prepareStatement("""
                        SELECT data_type,character_maximum_length,numeric_precision,numeric_scale,is_nullable
                        FROM information_schema.columns WHERE table_schema=? AND table_name=? AND column_name=?
                        """)) {
                    statement.setString(1, schema);
                    statement.setString(2, table);
                    statement.setString(3, name);
                    try (var result = statement.executeQuery()) {
                        assertThat(result.next()).as("Existe %s", label).isTrue();
                        boolean text = type == String.class && column != null
                                && "TEXT".equalsIgnoreCase(column.columnDefinition());
                        assertThat(result.getString("data_type")).as("Tipo %s", label)
                                .isEqualTo(postgresType(type, text));
                        if (!text && (type == String.class || type.isEnum())) {
                            assertThat(result.getInt("character_maximum_length")).as("Longitud %s", label)
                                    .isEqualTo(column == null ? 255 : column.length());
                        }
                        if (type == BigDecimal.class) {
                            assertThat(result.getInt("numeric_precision")).as("Precision %s", label)
                                    .isEqualTo(column.precision());
                            assertThat(result.getInt("numeric_scale")).as("Escala %s", label)
                                    .isEqualTo(column.scale());
                        }
                        boolean nullable = !field.isAnnotationPresent(Id.class)
                                && (join != null ? join.nullable() : column == null || column.nullable());
                        assertThat(result.getString("is_nullable")).as("Nulabilidad %s", label)
                                .isEqualTo(nullable ? "YES" : "NO");
                        checked++;
                    }
                }
            }
        }
        assertThat(checked).isGreaterThan(60);
        System.out.println("Columnas Java/PostgreSQL comprobadas: " + checked + " en " + entities.size() + " entidades");
        // La referencia UUID anterior es historica, no un campo que falte en ClienteEntity.
        assertThat(scalar("SELECT data_type FROM information_schema.columns WHERE table_schema='" + schema
                + "' AND table_name='clientes' AND column_name='id_anterior'")).isEqualTo("uuid");
    }

    private String postgresType(Class<?> type, boolean text) {
        if (type == String.class || type.isEnum()) return text ? "text" : "character varying";
        if (type == Long.class || type == long.class) return "bigint";
        if (type == Integer.class || type == int.class) return "integer";
        if (type == Boolean.class || type == boolean.class) return "boolean";
        if (type == UUID.class) return "uuid";
        if (type == BigDecimal.class) return "numeric";
        if (type == LocalDate.class) return "date";
        if (type == Instant.class) return "timestamp with time zone";
        if (type == LocalDateTime.class) return "timestamp without time zone";
        throw new IllegalArgumentException("Tipo de entidad sin verificar: " + type);
    }

    @Test
    void v1ConsolidadaReproduceElSchemaFinalYEsCompatibleConFlyway() throws Exception {
        flyway("16").migrate();
        String expected = schemaStructure();
        if (!schema.matches("schema_test_[a-f0-9]{32}")) throw new IllegalStateException("Schema invalido");
        sql("DROP SCHEMA " + schema + " CASCADE");
        // Flyway crea exclusivamente nuestro schema desechable, nunca public.
        assertThat(initialFlyway().migrate().migrationsExecuted).isEqualTo(1);
        sql("SET search_path TO " + schema);
        assertThat(schemaStructure()).isEqualTo(expected);
        initialFlyway().validate();
        assertThat(initialFlyway().migrate().migrationsExecuted).isZero();
        assertThat(scalar("SELECT count(*) FROM flyway_schema_history WHERE version IS NOT NULL"))
                .isEqualTo("1");
        assertThat(scalar("SELECT version FROM flyway_schema_history WHERE version IS NOT NULL"))
                .isEqualTo("1");
        assertThat(scalar("SELECT count(*) FROM clientes")).isEqualTo("0");
        assertThat(scalar("SELECT count(*) FROM app_users")).isEqualTo("0");
        assertThat(scalar("SELECT count(*) FROM gestopago_tokens")).isEqualTo("0");
        seed();
        assertThatExceptionOfType(SQLException.class).isThrownBy(() -> sql("UPDATE clientes SET activo=false"))
                .satisfies(error -> assertThat(error.getSQLState()).isEqualTo("23514"));
        sql("UPDATE cuentas SET activa=false");
        sql("UPDATE clientes SET activo=false");
    }

    @Test
    void v1ConsolidadaNoReemplazaElHistorialDeUnaBaseExistente() throws Exception {
        flyway("16").migrate();
        seed();
        String expected = schemaStructure();
        assertThatThrownBy(() -> initialFlyway().migrate()).isInstanceOf(RuntimeException.class);
        assertThat(schemaStructure()).isEqualTo(expected);
        assertThat(scalar("SELECT count(*) FROM flyway_schema_history WHERE version IS NOT NULL"))
                .isEqualTo("16");
        assertThat(scalar("SELECT count(*) FROM clientes")).isEqualTo("1");
        flyway("16").validate();
    }

    @Test
    void v1ConsolidadaRechazaUnaBaseConTablasSinHistorial() throws Exception {
        sql("CREATE TABLE existing_marker(id integer PRIMARY KEY)");
        sql("INSERT INTO existing_marker VALUES (1)");
        assertThatThrownBy(() -> initialFlyway().migrate()).isInstanceOf(RuntimeException.class);
        assertThat(scalar("SELECT count(*) FROM existing_marker")).isEqualTo("1");
        assertThat(scalar("SELECT count(*) FROM information_schema.tables WHERE table_schema='" + schema
                + "' AND table_name='clientes'")).isEqualTo("0");
    }

    private String schemaStructure() throws Exception {
        return scalar("""
                SELECT md5(string_agg(item,'|' ORDER BY item)) FROM (
                    SELECT concat_ws(':','column',table_name,column_name,data_type,
                        character_maximum_length,numeric_precision,numeric_scale,is_nullable,
                        is_identity,identity_generation,
                        replace(coalesce(column_default,''),current_schema()||'.','')) AS item
                    FROM information_schema.columns WHERE table_schema=current_schema()
                    UNION ALL
                    SELECT concat_ws(':','constraint',r.relname,c.conname,c.contype,
                        replace(pg_get_constraintdef(c.oid),current_schema()||'.',''))
                    FROM pg_constraint c JOIN pg_class r ON r.oid=c.conrelid
                    WHERE c.connamespace=current_schema()::regnamespace AND c.contype<>'n'
                    UNION ALL
                    SELECT concat_ws(':','index',tablename,indexname,
                        replace(indexdef,current_schema()||'.',''))
                    FROM pg_indexes WHERE schemaname=current_schema()
                    UNION ALL
                    SELECT concat_ws(':','trigger',trigger_name,event_object_table,
                        event_manipulation,action_timing,
                        replace(action_statement,current_schema()||'.',''))
                    FROM information_schema.triggers WHERE trigger_schema=current_schema()
                ) objects
                """);
    }

    @Test
    void instalacionNuevaEIdempotencia() throws Exception {
        assertThat(flyway("16").migrate().migrationsExecuted).isEqualTo(16);
        assertThat(flyway("16").migrate().migrationsExecuted).isZero();
        assertThat(scalar("SELECT count(*) FROM flyway_schema_history WHERE version='16' AND success")).isEqualTo("1");
        assertThat(scalar("SELECT count(*) FROM information_schema.tables WHERE table_schema='" + schema
                + "' AND table_name IN ('personas','accounts','legacy_personas','legacy_accounts')")).isEqualTo("0");
    }

    @Test
    void v16RetiraModelosAntiguosSinPerderRegistrosNiRelaciones() throws Exception {
        flyway("15").migrate();
        seed();
        seedLegacy();
        String before = snapshot();
        flyway("16").migrate();
        assertThat(scalar("SELECT count(*) FROM information_schema.tables WHERE table_schema='" + schema
                + "' AND table_name IN ('personas','accounts')")).isEqualTo("0");
        sql("ALTER TABLE legacy_personas RENAME TO personas");
        sql("ALTER TABLE legacy_accounts RENAME TO accounts");
        assertThat(snapshot()).isEqualTo(before);
        assertThatExceptionOfType(SQLException.class).isThrownBy(() -> sql(
                "UPDATE accounts SET user_id=md5('inexistente')::uuid"))
                .satisfies(error -> assertThat(error.getSQLState()).isEqualTo("23503"));
    }

    @Test
    void v15ConservaDatosYAdmiteLosLimitesSinRecortar() throws Exception {
        flyway("14").migrate();
        seed();
        seedLegacy();
        String before = snapshot();
        flyway("15").migrate();
        assertThat(snapshot()).isEqualTo(before);
        sql("UPDATE personas SET nombre=repeat('A',50),apellido_paterno=repeat('B',50),apellido_materno=repeat('C',50)");
        sql("UPDATE clientes SET nombre=repeat('A',50),segundo_nombre=repeat('B',50),"
                + "apellido_paterno=repeat('C',50),apellido_materno=repeat('D',50),sexo='NO_BINARIO',estado_civil='UNION_LIBRE'");
        sql("UPDATE app_users u SET full_name=concat_ws(' ',c.nombre,c.segundo_nombre,c.apellido_paterno,c.apellido_materno)"
                + " FROM clientes c WHERE c.id=u.cliente_id");
        assertThat(scalar("SELECT char_length(full_name) FROM app_users")).isEqualTo("203");
        // El limite pertenece al hash producido por el encoder, no a la contrasena.
        String hash = new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(4).encode("Segura123!");
        assertThat(hash).hasSize(60);
        try (var statement = connection.prepareStatement("UPDATE app_users SET password_hash=?")) {
            statement.setString(1, hash);
            statement.executeUpdate();
        }
        for (String column : List.of("nombre", "apellido_paterno", "apellido_materno")) {
            invalidLength("UPDATE personas SET " + column + "=repeat('A',51)");
        }
        invalidLength("UPDATE app_users SET full_name=repeat('A',204)");
        invalidLength("UPDATE app_users SET password_hash=repeat('A',61)");
        invalidLength("UPDATE accounts SET account_number=repeat('A',21)");
        invalidLength("UPDATE clientes SET sexo=repeat('A',11)");
        invalidLength("UPDATE clientes SET estado_civil=repeat('A',12)");
        invalidLength("UPDATE app_users SET rol=repeat('A',10)");
    }

    @ParameterizedTest
    @ValueSource(strings = {"nombre", "apellido_paterno", "apellido_materno", "full_name", "password_hash", "account_number"})
    void v15RechazaDatosHistoricosLargosSinRecortarNiAlterarElSchema(String column) throws Exception {
        flyway("14").migrate();
        seed();
        seedLegacy();
        String table = switch (column) {
            case "full_name", "password_hash" -> "app_users";
            case "account_number" -> "accounts";
            default -> "personas";
        };
        int limit = switch (column) {
            case "full_name" -> 203;
            case "password_hash" -> 60;
            case "account_number" -> 20;
            default -> 50;
        };
        sql("UPDATE " + table + " SET " + column + "=repeat('A'," + (limit + 1) + ")");
        String before = snapshot();
        assertThatThrownBy(() -> flyway("15").migrate()).isInstanceOf(RuntimeException.class);
        assertThat(snapshot()).isEqualTo(before);
        assertThat(scalar("SELECT count(*) FROM flyway_schema_history WHERE version='15'")).isEqualTo("0");
        assertThat(scalar("SELECT character_maximum_length FROM information_schema.columns WHERE table_schema='"
                + schema + "' AND table_name='personas' AND column_name='nombre'")).isEqualTo("150");
        assertThat(scalar("SELECT char_length(" + column + ") FROM " + table)).isEqualTo(Integer.toString(limit + 1));
    }

    private void invalidLength(String query) {
        assertThatExceptionOfType(SQLException.class).isThrownBy(() -> sql(query))
                .satisfies(error -> assertThat(error.getSQLState()).isEqualTo("22001"));
    }

    private void seedLegacy() throws SQLException {
        sql("INSERT INTO personas(nombre,apellido_paterno,apellido_materno) VALUES ('Marco','Morales','Martinez')");
        sql("INSERT INTO accounts(id,user_id,account_number,status) SELECT md5('legacy')::uuid,id,"
                + "'ACC-0000000000000001','ACTIVE' FROM app_users");
    }

    @Test
    void upgradeConservaRegistrosTiposHistoricosYUnicidad() throws Exception {
        flyway("13").migrate();
        seed();
        String before = snapshot();
        flyway("14").migrate();
        assertThat(snapshot()).isEqualTo(before);
        assertThat(scalar("SELECT count(*) FROM clientes WHERE id_anterior IS NOT NULL")).isEqualTo("1");
        assertThat(scalar("SELECT count(*) FROM pg_indexes WHERE schemaname='" + schema
                + "' AND indexname IN ('idx_app_users_email','idx_cuentas_cliente','idx_clientes_activos',"
                + "'idx_clientes_fecha_creacion','idx_clientes_apellidos','idx_domicilios_codigo_postal')")).isEqualTo("0");
        assertThat(scalar("SELECT count(*) FROM pg_indexes WHERE schemaname='" + schema
                + "' AND indexname IN ('idx_clientes_fecha_id','idx_clientes_inactivos_fecha_id',"
                + "'idx_cuentas_cliente_activa','uk_app_users_email','uk_app_users_email_normalized')")).isEqualTo("5");
        assertThatExceptionOfType(SQLException.class).isThrownBy(() -> sql(
                "INSERT INTO cuentas(id,cliente_id,numero_cuenta,saldo) SELECT md5('otra')::uuid,id,"
                + "'CTA-00000000000000000001',0 FROM clientes LIMIT 1"))
                .satisfies(error -> assertThat(error.getSQLState()).isEqualTo("23505"));
        assertThatExceptionOfType(SQLException.class).isThrownBy(() -> sql("UPDATE cuentas SET cliente_id=-1"))
                .satisfies(error -> assertThat(error.getSQLState()).isIn("23503", "23514"));
    }

    @Test
    void rechazaImportesNoFinitosNegativosYCeroIngreso() throws Exception {
        flyway("14").migrate();
        seed();
        for (String value : new String[]{"'NaN'", "-0.01"}) {
            invalidCheck("UPDATE cuentas SET saldo=" + value);
        }
        for (String value : new String[]{"'NaN'", "0", "-0.01"}) {
            invalidCheck("UPDATE clientes SET ingreso_mensual=" + value);
        }
        invalidCheck("UPDATE gestopago_products SET price='NaN'");
        for (String value : new String[]{"'Infinity'", "'-Infinity'"}) {
            assertThatExceptionOfType(SQLException.class).isThrownBy(() -> sql("UPDATE cuentas SET saldo=" + value))
                    .satisfies(error -> assertThat(error.getSQLState()).isIn("22003", "23514"));
        }
        sql("UPDATE cuentas SET saldo=0");
        sql("UPDATE clientes SET ingreso_mensual=0.01");
        sql("UPDATE gestopago_products SET price=NULL");
        assertThat(scalar("SELECT saldo FROM cuentas")).isEqualTo("0.00");
    }

    @Test
    void datosHistoricosInvalidosImpidenMigrarSinBorrarlos() throws Exception {
        flyway("13").migrate();
        seed();
        sql("UPDATE cuentas SET saldo='NaN'");
        assertThatThrownBy(() -> flyway("14").migrate()).isInstanceOf(RuntimeException.class);
        assertThat(scalar("SELECT count(*) FROM flyway_schema_history WHERE version='14'")).isEqualTo("0");
        assertThat(scalar("SELECT saldo FROM cuentas")).isEqualTo("NaN");
        assertThat(scalar("SELECT count(*) FROM pg_indexes WHERE schemaname='" + schema
                + "' AND indexname='idx_app_users_email'")).isEqualTo("1");
        assertThat(scalar("SELECT count(*) FROM clientes")).isEqualTo("1");
    }

    @Test
    void planesConTreintaMilClientesMantienenOrdenYReducenIndices() throws Exception {
        flyway("13").migrate();
        sql("""
                INSERT INTO clientes(nombre,apellido_paterno,apellido_materno,fecha_nacimiento,curp,rfc,
                    sexo,nacionalidad,estado_civil,correo,telefono_movil,ocupacion,empresa,ingreso_mensual,
                    activo,fecha_creacion)
                SELECT 'Cliente','Prueba','Carga','1990-01-01',prefix||'900101HDFBBB01',prefix||'900101AB1',
                    'MASCULINO','Mexicana','SOLTERO','c'||n||'@test.invalid',lpad(n::text,10,'0'),
                    'Ingeniero','Prueba',1000,n%10<>0,'2020-01-01'::timestamptz+n*interval '1 minute'
                FROM (SELECT n,chr(65+(n/4056)%26)||substr('AEIOUX',(n/676)%6+1,1)
                    ||chr(65+(n/26)%26)||chr(65+n%26) AS prefix FROM generate_series(1,30000) AS n) AS source
                """);
        sql("""
                INSERT INTO cuentas(id,cliente_id,numero_cuenta,saldo,activa)
                SELECT md5('cuenta-'||id)::uuid,id,'CTA-'||lpad(id::text,20,'0'),0,activo FROM clientes
                """);
        sql("ANALYZE clientes");
        sql("ANALYZE cuentas");
        long before = indexBytes();
        flyway("14").migrate();
        sql("ANALYZE clientes");
        sql("ANALYZE cuentas");
        long after = indexBytes();
        assertThat(after).isLessThan(before);
        assertThat(plan("SELECT * FROM clientes ORDER BY fecha_creacion DESC,id DESC LIMIT 100"))
                .contains("idx_clientes_fecha_id");
        assertThat(plan("SELECT * FROM clientes WHERE activo=TRUE ORDER BY fecha_creacion DESC,id DESC LIMIT 100"))
                .contains("idx_clientes_fecha_id");
        assertThat(plan("SELECT * FROM clientes WHERE activo=FALSE ORDER BY fecha_creacion DESC,id DESC LIMIT 100"))
                .contains("idx_clientes_inactivos_fecha_id");
        assertThat(plan("SELECT * FROM cuentas WHERE cliente_id=1000"))
                .contains("idx_cuentas_cliente_activa");
        assertThat(scalar("SELECT count(*) FROM clientes")).isEqualTo("30000");
        System.out.println("Indices en fixture sintetico de 30000 clientes/cuentas: antes=" + before + ", despues=" + after);
    }

    @Test
    void existenciaDeUsuariosUtilizaIndicesConTreintaMilRegistros() throws Exception {
        flyway("16").migrate();
        sql("""
                INSERT INTO app_users(id,email,identifier,password_hash,full_name)
                SELECT md5('usuario-'||n)::uuid,'usuario'||n||'@test.invalid','alias-'||n,
                    repeat('x',60),'Usuario de prueba'
                FROM generate_series(1,30000) AS n
                """);
        sql("ANALYZE app_users");
        String consulta = """
                select exists(select 1 from app_users where lower(trim(email)) = 'alias-15000')
                    or exists(select 1 from app_users where identifier = 'alias-15000')
                """;
        String planJson = scalar("EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON) " + consulta);
        assertThat(planJson).contains("uk_app_users_email_normalized", "uk_app_users_identifier")
                .doesNotContain("Seq Scan");
        assertThat(scalar(consulta)).isEqualTo("t");
        assertThat(scalar("select exists(select 1 from app_users where lower(trim(email))"
                + " = 'usuario15000@test.invalid')")).isEqualTo("t");
        assertThat(scalar("select count(*) from app_users")).isEqualTo("30000");
        var plan = new ObjectMapper().readTree(planJson).get(0);
        System.out.println("EXISTS con 30000 usuarios sinteticos: indices normalizado/identificador;"
                + " execution_ms=" + plan.get("Execution Time") + "; planning_ms=" + plan.get("Planning Time"));
    }

    private long indexBytes() throws Exception {
        return Long.parseLong(scalar("SELECT pg_indexes_size('clientes')+pg_indexes_size('cuentas')"
                + "+pg_indexes_size('domicilios')+pg_indexes_size('app_users')"));
    }

    private String plan(String query) throws Exception {
        String json = scalar("EXPLAIN (FORMAT JSON) " + query);
        return new ObjectMapper().readTree(json).toString();
    }

    private void invalidCheck(String query) {
        assertThatExceptionOfType(SQLException.class).isThrownBy(() -> sql(query))
                .satisfies(error -> assertThat(error.getSQLState()).isEqualTo("23514"));
    }

    private void seed() throws Exception {
        sql("""
                INSERT INTO clientes(id_anterior,nombre,apellido_paterno,apellido_materno,fecha_nacimiento,
                    curp,rfc,sexo,nacionalidad,estado_civil,correo,telefono_movil,ocupacion,empresa,ingreso_mensual)
                VALUES (md5('historico')::uuid,'Marco','Morales','Martinez','1990-01-01','MOMA900101HDFRRR01',
                    'MOMA900101AB1','MASCULINO','Mexicana','SOLTERO','marco@test.invalid',
                    '5512345678','Ingeniero','Prueba',1000)
                """);
        sql("""
                INSERT INTO domicilios(id,cliente_id,calle,numero_exterior,colonia,municipio,estado,codigo_postal,pais)
                SELECT md5('domicilio')::uuid,id,'Reforma','1','Centro','Cuauhtemoc','CDMX','01234','Mexico' FROM clientes
                """);
        sql("""
                INSERT INTO cuentas(id,cliente_id,numero_cuenta,saldo)
                SELECT md5('cuenta')::uuid,id,'CTA-00000000000000000001',1337.25 FROM clientes
                """);
        sql("""
                INSERT INTO app_users(id,email,identifier,password_hash,full_name,cliente_id)
                SELECT md5('usuario')::uuid,correo,correo,'hash-solo-fixture','Marco Morales Martinez',id FROM clientes
                """);
        sql("INSERT INTO gestopago_products(product_id,product_name,price) VALUES (1,'Producto de prueba',1.2345)");
    }

    private String snapshot() throws Exception {
        return scalar("""
                SELECT md5(string_agg(row_data,'|' ORDER BY row_data)) FROM (
                    SELECT to_jsonb(c)::text AS row_data FROM clientes c
                    UNION ALL SELECT to_jsonb(d)::text FROM domicilios d
                    UNION ALL SELECT to_jsonb(c)::text FROM cuentas c
                    UNION ALL SELECT to_jsonb(u)::text FROM app_users u
                    UNION ALL SELECT to_jsonb(p)::text FROM gestopago_products p
                    UNION ALL SELECT to_jsonb(p)::text FROM personas p
                    UNION ALL SELECT to_jsonb(a)::text FROM accounts a
                    UNION ALL SELECT to_jsonb(t)::text FROM gestopago_tokens t
                ) AS data
                """);
    }

    private String scalar(String query) throws Exception {
        try (var statement = connection.createStatement(); var result = statement.executeQuery(query)) {
            if (!result.next()) throw new IllegalStateException("Consulta sin resultado");
            return result.getString(1);
        }
    }

    private void sql(String query) throws SQLException {
        try (var statement = connection.createStatement()) {
            statement.execute(query);
        }
    }
}
