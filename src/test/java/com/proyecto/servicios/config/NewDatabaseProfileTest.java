package com.proyecto.servicios.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class NewDatabaseProfileTest {

    @Test
    void perfilNuevoSeleccionaSoloV1YUnaUrlExplicitaSinBaseline() {
        new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withPropertyValues(
                        "spring.profiles.active=new-database",
                        "spring.cloud.config.enabled=false",
                        "NEW_DATABASE_JDBC_URL=jdbc:postgresql://127.0.0.1:18549/nueva_fixture"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    var environment = context.getEnvironment();
                    assertThat(environment.getProperty("app.database.enabled", Boolean.class)).isTrue();
                    assertThat(environment.getProperty("spring.datasource.url"))
                            .isEqualTo("jdbc:postgresql://127.0.0.1:18549/nueva_fixture");
                    assertThat(environment.getProperty("spring.flyway.locations"))
                            .isEqualTo("classpath:db/initial");
                    assertThat(environment.getProperty("spring.flyway.baseline-on-migrate", Boolean.class))
                            .isFalse();
                });
    }
}
