package com.proyecto.servicios.model.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import com.proyecto.servicios.model.cliente.ContrasenaSegura;

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
        @ContrasenaSegura
        String password,

        @NotBlank(message = "El nombre es obligatorio")
        @Size(min = 2, max = 203, message = "El nombre completo debe tener entre 2 y 203 caracteres")
        @Pattern(
                regexp = "^(?!.*\\s{2,})\\S(?:.*\\S)?$",
                message = "El nombre no puede iniciar o terminar con espacios ni contener espacios consecutivos"
        )
        String fullName
) {
    @Override
    public String toString() {
        return "RegisterRequest[datos personales y contrasena protegidos]";
    }
}
