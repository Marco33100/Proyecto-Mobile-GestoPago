package com.proyecto.servicios.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.proyecto.servicios.model.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;

@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public RestAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void serviceUnavailable(HttpServletRequest request, HttpServletResponse response) throws IOException {
        writeError(request, response, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                "AUTH_DATABASE_ERROR", "El servicio de autenticacion no esta disponible");
    }

    public void forbidden(HttpServletRequest request, HttpServletResponse response) throws IOException {
        writeError(request, response, HttpServletResponse.SC_FORBIDDEN,
                "AUTH_FORBIDDEN", "No tienes permisos para realizar esta operacion");
    }

    public void tooManyRequests(HttpServletRequest request, HttpServletResponse response, long retryAfter)
            throws IOException {
        response.setHeader("Retry-After", Long.toString(retryAfter));
        writeError(request, response, 429, "AUTH_TOO_MANY_ATTEMPTS",
                "Demasiados intentos de inicio de sesion. Espera antes de intentar nuevamente.");
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authenticationException) throws IOException {
        writeError(request, response, HttpServletResponse.SC_UNAUTHORIZED, "AUTH_INVALID_SESSION",
                "La sesion es invalida, expiro o fue reemplazada por un nuevo inicio de sesion");
    }

    private void writeError(HttpServletRequest request, HttpServletResponse response,
                            int status, String code, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader("Cache-Control", "no-store");
        objectMapper.writeValue(response.getOutputStream(), new ApiErrorResponse(
                Instant.now(), status, code, message, request.getRequestURI(), Map.of()
        ));
    }
}
