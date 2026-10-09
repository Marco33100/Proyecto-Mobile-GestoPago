package com.proyecto.servicios.model.cliente;

import java.util.UUID;

public record UsuarioClienteModificadoEvent(UUID usuarioId, String correoAnterior, String correoActual) {
}
