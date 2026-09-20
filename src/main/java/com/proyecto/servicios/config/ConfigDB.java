package com.proyecto.servicios.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManagerFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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


@Slf4j
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
    @Autowired
    private Environment env;

    @Bean(name="sfDatasource")
    public DataSource sfDatasource(){
        HikariConfig config=new HikariConfig();
        try{
            config.setJdbcUrl(env.getProperty("spring.datasource.url"));
            config.setPassword(env.getProperty("spring.datasource.password"));
            config.setUsername(env.getProperty("spring.datasource.username"));
            config.setMaximumPoolSize(10);
            config.setMaxLifetime(1_800_000);
            config.setConnectionTimeout(5000);
            config.setValidationTimeout(5000);
            config.setMinimumIdle(2);
            config.setConnectionTestQuery("SELECT 1");
            config.setPoolName("sfDatasource");

        }catch (Exception e){
            log.error("Ha ocurrido un error en la conexcion a base de datos, a causa de:",e);
            return null;
        }
        return new HikariDataSource(config);
    }

    @Bean(name="sfEntityManagerFactory")
    @DependsOn("flyway")
    public LocalContainerEntityManagerFactoryBean sfEntityManagerFactory(){
        LocalContainerEntityManagerFactoryBean em= new LocalContainerEntityManagerFactoryBean();
        try{
          em.setDataSource(sfDatasource());
          em.setPackagesToScan(
                  "com.proyecto.servicios.entity.sf",
                  "com.proyecto.servicios.entity.gestopago"
          );
          em.setPersistenceUnitName("sfDatasource");
            HibernateJpaVendorAdapter vendorAdapter = new HibernateJpaVendorAdapter();
            em.setJpaVendorAdapter(vendorAdapter);
          Map<String, Object> properties=new HashMap<>();
          properties.put("hibernate.hbm2ddl.auto", "none");
            properties.put("hibernate.show-sql", false);
            properties.put("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");
            properties.put("hibernate.jdbc.batch_size", 100);
            properties.put("hibernate.order_inserts", true);
            properties.put("hibernate.order_updates", true);
            properties.put("jakarta.persistence.query.timeout", 600000);
            em.setJpaPropertyMap(properties);


        } catch (Exception e) {
            log.error("Ha ocurrido un error en la conexion a base de datos, a causa de:",e);
            return null;

        }
        return em;
    }
 @Bean(name="sfTransactionManager")
 public PlatformTransactionManager sfTransactionManager(@Qualifier("sfEntityManagerFactory") EntityManagerFactory sfEntityManagerFactory){
        return new JpaTransactionManager(sfEntityManagerFactory);

 }

}
