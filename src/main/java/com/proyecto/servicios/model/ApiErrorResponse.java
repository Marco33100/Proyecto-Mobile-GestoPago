package com.proyecto.servicios.model;

import java.time.Instant;
import java.util.Map;

/** Contrato comun para errores MVC y errores emitidos por los filtros de seguridad. */
public record ApiErrorResponse(
        Instant timestamp,
        int status,
        String code,
        String message,
        String path,
        Map<String, String> fieldErrors
) {
    public ApiErrorResponse {
        fieldErrors = fieldErrors == null ? Map.of() : Map.copyOf(fieldErrors);
    }
}
