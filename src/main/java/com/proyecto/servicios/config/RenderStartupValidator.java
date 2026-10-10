package com.proyecto.servicios.config;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;

/** Evita publicar una instancia con clave temporal o conexiones incompletas. */
@Component
@Profile("render")
public class RenderStartupValidator implements InitializingBean {
    private final Environment environment;

    public RenderStartupValidator(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void afterPropertiesSet() {
        String secret = environment.getRequiredProperty("auth.jwt.secret");
        if (secret.isBlank() || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("Render requiere AUTH_JWT_SECRET de al menos 32 bytes");
        }
        String jdbcUrl = environment.getRequiredProperty("spring.datasource.url");
        if (!jdbcUrl.startsWith("jdbc:postgresql://")) {
            throw new IllegalStateException("SPRING_DATASOURCE_URL debe ser una URL JDBC PostgreSQL");
        }
        requireNotBlank("spring.datasource.username", "SPRING_DATASOURCE_USERNAME");
        requireNotBlank("spring.datasource.password", "SPRING_DATASOURCE_PASSWORD");
        try {
            URI redis = new URI(environment.getRequiredProperty("spring.data.redis.url"));
            if (!("redis".equals(redis.getScheme()) || "rediss".equals(redis.getScheme()))
                    || redis.getHost() == null) {
                throw new IllegalStateException("REDIS_URL debe ser una URL redis:// o rediss:// valida");
            }
            URI publicUrl = new URI(environment.getRequiredProperty("api.documentation.server-url"));
            if (!"https".equals(publicUrl.getScheme()) || publicUrl.getHost() == null) {
                throw new IllegalStateException("La URL publica de Swagger en Render debe usar HTTPS");
            }
        } catch (URISyntaxException exception) {
            // No incluir la URI original: puede contener password o token.
            throw new IllegalStateException("La configuracion de URLs para Render no es valida");
        }
    }

    private void requireNotBlank(String property, String variable) {
        if (environment.getRequiredProperty(property).isBlank()) {
            throw new IllegalStateException("Render requiere " + variable);
        }
    }
}
