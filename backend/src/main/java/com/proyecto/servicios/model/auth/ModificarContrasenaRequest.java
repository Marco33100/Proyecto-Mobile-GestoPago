package com.proyecto.servicios.model.auth;

import com.proyecto.servicios.model.SolicitudJson;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ModificarContrasenaRequest(
        @NotBlank(message = "El nombre es obligatorio y no puede contener solo espacios.")
        @Size(max = 203) String nombre,
        @NotBlank(message = "El correo electrónico es obligatorio.")
        @Email(message = "El correo electrónico no tiene un formato válido.")
        @Size(max = 254) String correo,
        @NotNull(message = "Las credenciales son obligatorias") @Valid CambiarContrasenaRequest credenciales
) implements SolicitudJson {
    @Override
    public String toString() { return "ModificarContrasenaRequest[credenciales protegidas]"; }
}
