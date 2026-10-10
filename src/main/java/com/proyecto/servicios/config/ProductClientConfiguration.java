package com.proyecto.servicios.config;

import com.proyecto.servicios.exception.ProductIntegrationErrorType;
import com.proyecto.servicios.exception.ProductIntegrationException;
import com.proyecto.servicios.exception.GestoPagoTokenRejectedException;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.Logger;
import feign.Response;
import feign.Retryer;
import feign.RequestInterceptor;
import feign.codec.ErrorDecoder;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.io.IOException;

public class ProductClientConfiguration {

    private static final String API_KEY_HEADER = "X-API-Key";

    @Bean
    RequestInterceptor productHeadersInterceptor(ProductServiceProperties properties) {
        return requestTemplate -> {
            requestTemplate.header(HttpHeaders.ACCEPT, MediaType.APPLICATION_XML_VALUE);

            if (StringUtils.hasText(properties.getApiKey())) {
                requestTemplate.header(API_KEY_HEADER, properties.getApiKey().trim());
            }
        };
    }

    @Bean
    Logger productLogger() {
        return new Logger.NoOpLogger();
    }

    @Bean
    Retryer productRetryer() {
        // El unico reintento se hace explicitamente en el gateway tras renovar el token.
        return Retryer.NEVER_RETRY;
    }

    @Bean
    ErrorDecoder productErrorDecoder() {
        return (methodKey, response) -> {
            if (response.status() == 401 || (response.status() == 403 && isExpiredToken(response))) {
                return new GestoPagoTokenRejectedException();
            }
            if (response.status() == 401 || response.status() == 403) {
                return new ProductIntegrationException(
                        ProductIntegrationErrorType.GESTOPAGO_UNAVAILABLE,
                        "Gestopago rechazó las credenciales de autenticación"
                );
            }

            return new ProductIntegrationException(
                    ProductIntegrationErrorType.GESTOPAGO_UNAVAILABLE,
                    "Gestopago respondió con un estado HTTP no exitoso: " + response.status()
            );
        };
    }

    private boolean isExpiredToken(Response response) {
        if (response.body() == null) {
            return false;
        }
        // Reconocer solo el JSON documentado; no registrar ni conservar el cuerpo externo.
        try (InputStream input = response.body().asInputStream()) {
            byte[] payload = input.readNBytes(4097);
            if (payload.length > 4096) {
                return false;
            }
            var json = new ObjectMapper().readTree(payload);
            return json != null && "EXPIRED".equals(json.path("token").asText());
        } catch (IOException exception) {
            return false;
        }
    }
}
