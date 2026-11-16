package com.proyecto.servicios.model.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Segundo paso del inicio de sesion. Se envia el codigo del autenticador o,
 * si el usuario ya lo tiene configurado, uno de sus codigos de recuperacion.
 */
public record VerificarLoginRequest(
        @NotBlank @Size(max = 2048) String desafio,
        @Pattern(regexp = "\\d{6}", message = "El codigo debe tener seis digitos") String codigo,
        @Pattern(regexp = "(?i)[A-F0-9]{8}(-[A-F0-9]{8}){3}",
                message = "El codigo de recuperacion debe tener cuatro grupos de ocho caracteres")
        String codigoRecuperacion
) {
}
