package com.proyecto.servicios.model.auth;

import com.proyecto.servicios.model.cliente.ContrasenaSegura;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CambiarContrasenaRequest(
        @NotBlank @Size(max = 72)
        @Schema(accessMode = Schema.AccessMode.WRITE_ONLY) String contrasenaActual,
        @NotBlank @ContrasenaSegura
        @Schema(accessMode = Schema.AccessMode.WRITE_ONLY) String nuevaContrasena
) {
    @Override
    public String toString() {
        return "CambiarContrasenaRequest[credenciales protegidas]";
    }
}
