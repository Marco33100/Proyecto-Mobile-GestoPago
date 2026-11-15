package com.proyecto.servicios.model.auth;

import java.util.UUID;

public record AuthenticatedUser(
        UUID userId,
        UUID sessionId,
        String email,
        String identifier
) {
}
