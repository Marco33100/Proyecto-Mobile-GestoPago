package com.proyecto.servicios.model.auth;

import java.util.UUID;

public record RegisterResponse(
        UUID userId,
        UUID accountId,
        String accountNumber,
        String message
) {
}
