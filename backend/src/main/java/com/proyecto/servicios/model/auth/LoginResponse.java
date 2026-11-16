package com.proyecto.servicios.model.auth;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/**
 * Sesion emitida tras verificar contrasena y segundo factor.
 *
 * @param codigosRecuperacion solo al vincular el autenticador por primera vez; se muestran una vez
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LoginResponse(
        String accessToken,
        String tokenType,
        Instant expiresAt,
        UserProfileResponse user,
        List<String> codigosRecuperacion
) {
    public LoginResponse(String accessToken, String tokenType, Instant expiresAt, UserProfileResponse user) {
        this(accessToken, tokenType, expiresAt, user, null);
    }

    public LoginResponse conCodigosRecuperacion(List<String> codigos) {
        return new LoginResponse(accessToken, tokenType, expiresAt, user, codigos);
    }

    /** Version sin JWT en el cuerpo para el cliente web (el JWT viaja en la cookie). */
    public LoginResponse sinToken() {
        return new LoginResponse(null, tokenType, expiresAt, user, codigosRecuperacion);
    }
}
