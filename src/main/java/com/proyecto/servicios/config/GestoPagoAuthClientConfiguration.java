package com.proyecto.servicios.config;

import feign.Logger;
import feign.RequestInterceptor;
import feign.codec.ErrorDecoder;
import org.springframework.context.annotation.Bean;
import java.net.URI;

/** Configuracion exclusiva de este cliente; el proveedor exige secretos en query params. */
public class GestoPagoAuthClientConfiguration {
    @Bean
    Logger authLogger() {
        // NoOp evita fugas aun si alguien configura accidentalmente loggerLevel=FULL.
        return new Logger.NoOpLogger();
    }

    @Bean
    ErrorDecoder authErrorDecoder() {
        // Nunca adjuntar URL, cuerpo de respuesta ni FeignException con credenciales.
        return (method, response) -> new IllegalStateException(
                "El servicio de autenticacion Gestopago rechazo la solicitud: HTTP " + response.status());
    }

    @Bean
    RequestInterceptor authHttpsOnly() {
        return request -> {
            URI target = URI.create(request.feignTarget().url());
            if (!"https".equalsIgnoreCase(target.getScheme())) {
                throw new IllegalStateException("La autenticacion Gestopago requiere HTTPS");
            }
        };
    }
}
