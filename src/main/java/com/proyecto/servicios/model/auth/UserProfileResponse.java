package com.proyecto.servicios.model.auth;

import java.util.UUID;

public record UserProfileResponse(
        UUID id,
        String email,
        String identifier,
        String fullName
) {
}
