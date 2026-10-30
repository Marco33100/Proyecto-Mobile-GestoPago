package com.proyecto.servicios.entity;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.sql.*;
import java.util.*;

/** Ejecuta los mismos escenarios financieros con PostgreSQL y el DDL real en un schema desechable. */
@EnabledIfEnvironmentVariable(named="ONBOARDING_TEST_JDBC_URL",matches=".+")
class PagoFlowPostgresTest extends PagoFlowPersistenceTest {
    private Connection connection;
    private String schema;
    @Override protected Map<String,String> configuracionPersistencia() throws Exception {
        String url=System.getenv("ONBOARDING_TEST_JDBC_URL");
        String user=System.getenv().getOrDefault("ONBOARDING_TEST_DB_USER","onboarding_test");
        String password=System.getenv().getOrDefault("ONBOARDING_TEST_DB_PASSWORD","");
        schema="payment_test_"+UUID.randomUUID().toString().replace("-","");
        connection=DriverManager.getConnection(url,user,password);
        try(var statement=connection.createStatement()) { statement.execute("CREATE SCHEMA "+schema); }
        Flyway.configure().dataSource(url,user,password).schemas(schema).defaultSchema(schema)
                .locations("classpath:db/migration","classpath:db/payments").load().migrate();
        String scopedUrl=url+(url.contains("?")?"&":"?")+"currentSchema="+schema;
        return Map.of("hibernate.connection.driver_class","org.postgresql.Driver","hibernate.connection.url",scopedUrl,
                "hibernate.connection.username",user,"hibernate.connection.password",password,
                "hibernate.default_schema",schema,"hibernate.hbm2ddl.auto","validate");
    }
    @Override protected void limpiarPersistencia() throws Exception {
        if(connection!=null) {
            try {
                if(schema==null || !schema.matches("payment_test_[a-f0-9]{32}")) throw new IllegalStateException("Schema de prueba invalido");
                try(var statement=connection.createStatement()) { statement.execute("DROP SCHEMA "+schema+" CASCADE"); }
            } finally { connection.close(); }
        }
    }
}
