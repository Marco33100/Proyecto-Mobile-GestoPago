package com.proyecto.servicios.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;


@Configuration
@ConditionalOnProperty(name = "app.database.enabled", havingValue = "true")
@EnableTransactionManagement
@EnableJpaRepositories(
        basePackages = {
                "com.proyecto.servicios.repositorys.sf",
                "com.proyecto.servicios.repositorys.gestopago"
        },
        transactionManagerRef = "sfTransactionManager",
        entityManagerFactoryRef = "sfEntityManagerFactory"
)
public class ConfigDB {

    private final Environment environment;

    public ConfigDB(Environment environment) {
        this.environment = environment;
    }

    @Bean(name = "sfDatasource")
    public DataSource sfDatasource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(environment.getProperty("spring.datasource.url"));
        config.setPassword(environment.getProperty("spring.datasource.password"));
        config.setUsername(environment.getProperty("spring.datasource.username"));
        config.setMaximumPoolSize(10);
        config.setMaxLifetime(1_800_000);
        config.setConnectionTimeout(5000);
        config.setValidationTimeout(5000);
        config.setMinimumIdle(2);
        config.setConnectionTestQuery("SELECT 1");
        config.setPoolName("sfDatasource");
        return new HikariDataSource(config);
    }

    @Bean(name = "sfEntityManagerFactory")
    @DependsOn("flyway")
    public LocalContainerEntityManagerFactoryBean sfEntityManagerFactory(
            @Qualifier("sfDatasource") DataSource dataSource
    ) {
        LocalContainerEntityManagerFactoryBean entityManagerFactory =
                new LocalContainerEntityManagerFactoryBean();
        entityManagerFactory.setDataSource(dataSource);
        entityManagerFactory.setPackagesToScan(
                "com.proyecto.servicios.entity.sf",
                "com.proyecto.servicios.entity.gestopago"
        );
        entityManagerFactory.setPersistenceUnitName("sfDatasource");
        HibernateJpaVendorAdapter vendorAdapter = new HibernateJpaVendorAdapter();
        entityManagerFactory.setJpaVendorAdapter(vendorAdapter);

        Map<String, Object> properties = new HashMap<>();
        properties.put("hibernate.hbm2ddl.auto", "none");
        properties.put("hibernate.show-sql", false);
        properties.put("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");
        properties.put("hibernate.jdbc.batch_size", 100);
        properties.put("hibernate.order_inserts", true);
        properties.put("hibernate.order_updates", true);
        properties.put("jakarta.persistence.query.timeout", 600000);
        entityManagerFactory.setJpaPropertyMap(properties);
        return entityManagerFactory;
    }

    @Bean(name = "sfTransactionManager")
    public PlatformTransactionManager sfTransactionManager(
            @Qualifier("sfEntityManagerFactory") EntityManagerFactory entityManagerFactory
    ) {
        return new JpaTransactionManager(entityManagerFactory);
    }

}
