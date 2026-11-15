package com.proyecto.servicios.model.auth;

import java.util.UUID;

public record SesionRevocadaEvent(UUID usuarioId, UUID sesionId) {
}
