package com.proyecto.servicios.model.cliente;

import com.proyecto.servicios.model.SolicitudJson;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ClienteIdRequest(
        @NotNull(message = "El ID es obligatorio")
        @Positive(message = "El ID debe ser mayor que cero") Long id
) implements SolicitudJson { }
