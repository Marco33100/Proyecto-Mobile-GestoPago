package com.proyecto.servicios.model.auth;

import java.time.Instant;

public record LoginResponse(
        String accessToken,
        String tokenType,
        Instant expiresAt,
        UserProfileResponse user
) {
}
