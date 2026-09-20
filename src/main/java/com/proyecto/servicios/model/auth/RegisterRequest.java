package com.proyecto.servicios.model.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "El correo es obligatorio")
        @Email(message = "El correo no tiene un formato valido")
        @Size(max = 254, message = "El correo no puede superar 254 caracteres")
        @Pattern(regexp = "^\\S+$", message = "El correo no puede contener espacios")
        String email,

        @NotBlank(message = "El identificador es obligatorio")
        @Pattern(
                regexp = "^[A-Za-z0-9._-]{4,50}$",
                message = "El identificador debe tener de 4 a 50 caracteres alfanumericos, punto, guion o guion bajo"
        )
        String identifier,

        @NotBlank(message = "La contrasena es obligatoria")
        @Size(min = 8, max = 72, message = "La contrasena debe tener entre 8 y 72 caracteres")
        @Pattern(
                regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).+$",
                message = "La contrasena debe incluir mayuscula, minuscula y numero"
        )
        String password,

        @NotBlank(message = "El nombre es obligatorio")
        @Size(min = 2, max = 150, message = "El nombre debe tener entre 2 y 150 caracteres")
        @Pattern(
                regexp = "^(?!.*\\s{2,})\\S(?:.*\\S)?$",
                message = "El nombre no puede iniciar o terminar con espacios ni contener espacios consecutivos"
        )
        String fullName
) {
}
