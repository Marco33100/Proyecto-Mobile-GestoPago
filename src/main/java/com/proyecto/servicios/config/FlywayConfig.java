package com.proyecto.servicios.config;

import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.Flyway;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

@Slf4j
@Configuration
@ConditionalOnProperty(name = "app.database.enabled", havingValue = "true")
public class FlywayConfig {

    private final String[] locations;
    private final String historyTable;
    private final String schema;

    public FlywayConfig(
            @Value("${spring.flyway.locations:classpath:db/migration}") String[] locations,
            @Value("${spring.flyway.table:flyway_schema_history}") String historyTable,
            @Value("${spring.flyway.schemas:public}") String schema
    ) {
        this.locations = locations;
        this.historyTable = historyTable;
        this.schema = schema;
    }

    @Bean(name = "flyway")
    public Flyway flyway(@Qualifier("sfDatasource") DataSource dataSource) {
        log.info("Iniciando migraciones Flyway en schema '{}'", schema);
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations(locations)
                .table(historyTable)
                .schemas(schema)
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .load();
        flyway.migrate();
        return flyway;
    }
}
