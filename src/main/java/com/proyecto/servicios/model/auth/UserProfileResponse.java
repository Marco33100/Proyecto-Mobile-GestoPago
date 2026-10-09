package com.proyecto.servicios.model.auth;

import com.proyecto.servicios.entity.sf.RolUsuario;

import java.util.UUID;

public record UserProfileResponse(
        UUID id,
        String email,
        String identifier,
        String fullName,
        RolUsuario rol
) {
}
