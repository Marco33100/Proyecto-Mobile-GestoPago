package com.proyecto.servicios.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class RenderProfileTest {
    private ApplicationContextRunner configuredRender() {
        return new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withUserConfiguration(RenderStartupValidator.class)
                .withPropertyValues(
                        "spring.profiles.active=render",
                        "SPRING_DATASOURCE_URL=jdbc:postgresql://postgres.fixture:5432/gestopago",
                        "SPRING_DATASOURCE_USERNAME=fixture",
                        "SPRING_DATASOURCE_PASSWORD=fixture-only",
                        "REDIS_URL=redis://redis.fixture:6379",
                        "AUTH_JWT_SECRET=fixture-only-secret-at-least-32-bytes",
                        "RENDER_EXTERNAL_URL=https://gestopago-fixture.onrender.com"
                );
    }

    @Test
    void configuraPuertoPerfilV1YSaludSinCargarConfiguracionLocal() {
        configuredRender().withPropertyValues("PORT=12345").run(context -> {
            assertThat(context).hasNotFailed();
            var env = context.getEnvironment();
            assertThat(env.getActiveProfiles()).containsExactly("render");
            assertThat(env.getProperty("server.port", Integer.class)).isEqualTo(12345);
            assertThat(env.getProperty("server.address")).isEqualTo("0.0.0.0");
            assertThat(env.getProperty("app.database.enabled", Boolean.class)).isTrue();
            assertThat(env.getProperty("spring.flyway.locations")).isEqualTo("classpath:db/initial");
            assertThat(env.getProperty("spring.flyway.baseline-on-migrate", Boolean.class)).isFalse();
            assertThat(env.getProperty("spring.data.redis.url")).isEqualTo("redis://redis.fixture:6379");
            assertThat(env.getProperty("api.documentation.server-url"))
                    .isEqualTo("https://gestopago-fixture.onrender.com");
            assertThat(env.getProperty("management.health.redis.enabled", Boolean.class)).isFalse();
            assertThat(env.getProperty("management.endpoint.health.show-details")).isEqualTo("never");
            assertThat(env.getProperty("auth.bootstrap-executive.enabled", Boolean.class)).isFalse();
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"AUTH_JWT_SECRET=", "AUTH_JWT_SECRET=corta",
            "SPRING_DATASOURCE_URL=postgresql://postgres.fixture/gestopago",
            "SPRING_DATASOURCE_USERNAME=", "SPRING_DATASOURCE_PASSWORD=",
            "REDIS_URL=https://redis.fixture", "REDIS_URL=redis://",
            "RENDER_EXTERNAL_URL=http://gestopago-fixture.onrender.com"})
    void rechazaConfiguracionInseguraOIncompleta(String invalidProperty) {
        configuredRender().withPropertyValues(invalidProperty).run(context ->
                assertThat(context).hasFailed());
    }

    @Test
    void permiteUsarElHistorialOriginalSoloSiSeSeleccionaExplicitamente() {
        configuredRender().withPropertyValues("SPRING_FLYWAY_LOCATIONS=classpath:db/migration")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getEnvironment().getProperty("spring.flyway.locations"))
                            .isEqualTo("classpath:db/migration");
                });
    }
}
