package com.proyecto.servicios.model.auth;

import java.time.Instant;
import java.util.UUID;

public record UsuarioResponse(UUID id, Long clienteId, String correo, boolean activo,
                              Instant fechaCreacion, Instant fechaActualizacion) {
}
