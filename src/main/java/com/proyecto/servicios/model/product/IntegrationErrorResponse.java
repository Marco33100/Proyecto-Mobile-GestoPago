package com.proyecto.servicios.model.product;

import java.time.Instant;

public record IntegrationErrorResponse(
        Instant timestamp,
        int code,
        String message,
        String path
) {
}
