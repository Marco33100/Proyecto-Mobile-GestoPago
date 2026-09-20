package com.proyecto.servicios.model.auth;

import java.time.Instant;

public record SecurityErrorResponse(
        Instant timestamp,
        int status,
        String code,
        String message,
        String path
) {
}
