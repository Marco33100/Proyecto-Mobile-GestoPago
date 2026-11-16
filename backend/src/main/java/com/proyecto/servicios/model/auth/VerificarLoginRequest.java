package com.proyecto.servicios.model.auth;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.proyecto.servicios.model.cliente.BooleanEstrictoDeserializer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Segundo paso del inicio de sesion. Se envia el codigo del autenticador o,
 * si el usuario ya lo tiene configurado, uno de sus codigos de recuperacion.
 * {@code cerrarSesionAnterior} confirma que se cierre la sesion abierta en otro dispositivo.
 */
public record VerificarLoginRequest(
        @NotBlank @Size(max = 2048) String desafio,
        @Pattern(regexp = "\\d{6}", message = "El codigo debe tener seis digitos") String codigo,
        @Pattern(regexp = "(?i)[A-F0-9]{8}(-[A-F0-9]{8}){3}",
                message = "El codigo de recuperacion debe tener cuatro grupos de ocho caracteres")
        String codigoRecuperacion,
        @JsonDeserialize(using = BooleanEstrictoDeserializer.class) Boolean cerrarSesionAnterior
) {
    public VerificarLoginRequest(String desafio, String codigo, String codigoRecuperacion) {
        this(desafio, codigo, codigoRecuperacion, null);
    }

    public boolean confirmaCerrarSesionAnterior() {
        return Boolean.TRUE.equals(cerrarSesionAnterior);
    }
}
