package com.proyecto.servicios.model.cliente;

import com.proyecto.servicios.model.SolicitudJson;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** El ID identifica al cliente; datos solo contiene las propiedades modificables existentes. */
public record ModificarClienteRequest(
        @NotNull(message = "El ID es obligatorio")
        @Positive(message = "El ID debe ser mayor que cero") Long id,
        @NotNull(message = "Los datos del cliente son obligatorios") @Valid ActualizarClienteRequest datos
) implements SolicitudJson { }
