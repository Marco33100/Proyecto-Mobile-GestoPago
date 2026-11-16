package com.proyecto.servicios.model.auth;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * Primer paso del inicio de sesion: la contrasena es correcta y falta el segundo factor.
 *
 * @param desafio token firmado de un solo proposito que se envia en la verificacion
 * @param segundoFactorConfigurado false si el usuario debe vincular su autenticador ahora
 * @param configuracion clave y URI otpauth para vincularlo; solo cuando no esta configurado
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LoginDesafioResponse(
        String desafio,
        boolean segundoFactorConfigurado,
        Instant venceEn,
        Configuracion configuracion
) {
    public record Configuracion(String secreto, String uri) {
    }
}
