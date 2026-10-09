package com.proyecto.servicios.model.cliente;

import java.time.Instant;
import java.util.Map;

public record ClienteErrorResponse(
        Instant timestamp,
        int status,
        String code,
        String message,
        String path,
        Map<String, String> validationErrors
) {
}
