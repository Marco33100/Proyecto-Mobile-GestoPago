package com.proyecto.servicios.config;

import com.proyecto.servicios.exception.ProductIntegrationErrorType;
import com.proyecto.servicios.exception.ProductIntegrationException;
import feign.RequestInterceptor;
import feign.codec.ErrorDecoder;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;

public class ProductClientConfiguration {

    private static final String API_KEY_HEADER = "X-API-Key";

    @Bean
    RequestInterceptor productAuthenticationInterceptor(ProductServiceProperties properties) {
        return requestTemplate -> {
            if (!StringUtils.hasText(properties.getBearerToken())) {
                throw new ProductIntegrationException(
                        ProductIntegrationErrorType.GESTOPAGO_UNAVAILABLE,
                        "No se configuró el Bearer Token de Gestopago"
                );
            }

            requestTemplate.header(
                    HttpHeaders.AUTHORIZATION,
                    "Bearer " + properties.getBearerToken().trim()
            );
            requestTemplate.header(HttpHeaders.ACCEPT, MediaType.APPLICATION_XML_VALUE);

            if (StringUtils.hasText(properties.getApiKey())) {
                requestTemplate.header(API_KEY_HEADER, properties.getApiKey().trim());
            }
        };
    }

    @Bean
    ErrorDecoder productErrorDecoder() {
        return (methodKey, response) -> {
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
}
