package com.proyecto.servicios.model.auth;

import java.util.UUID;

public record CachedUser(
        UUID id,
        String email,
        String identifier,
        String passwordHash,
        String fullName,
        boolean enabled
) {
}
