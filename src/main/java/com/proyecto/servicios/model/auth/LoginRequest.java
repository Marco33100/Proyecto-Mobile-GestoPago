package com.proyecto.servicios.model.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank(message = "El correo es obligatorio")
        @Email(message = "El correo no tiene un formato valido")
        @Size(max = 254, message = "El correo no puede superar 254 caracteres")
        @jakarta.validation.constraints.Pattern(
                regexp = "^\\S+$",
                message = "El correo no puede contener espacios"
        )
        String email,

        @NotBlank(message = "La contrasena es obligatoria")
        @Size(max = 72, message = "La contrasena no puede superar 72 caracteres")
        String password
) {
}
