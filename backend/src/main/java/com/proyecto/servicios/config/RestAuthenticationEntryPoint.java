package com.proyecto.servicios.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.proyecto.servicios.entity.sf.PlataformaSesion;
import com.proyecto.servicios.model.GenericResponse;
import com.proyecto.servicios.model.auth.SesionActivaResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public RestAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void serviceUnavailable(HttpServletRequest request, HttpServletResponse response) throws IOException {
        writeError(request, response, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                "El servicio de autenticacion no esta disponible");
    }

    public void forbidden(HttpServletRequest request, HttpServletResponse response) throws IOException {
        writeError(request, response, HttpServletResponse.SC_FORBIDDEN,
                "No tienes permisos para realizar esta operacion");
    }

    public void tooManyRequests(HttpServletRequest request, HttpServletResponse response, long retryAfter)
            throws IOException {
        response.setHeader("Retry-After", Long.toString(retryAfter));
        writeError(request, response, 429,
                "Demasiados intentos de inicio de sesion. Espera antes de intentar nuevamente.");
    }

    /** 401 con la plataforma de la sesion nueva, para que la app explique por que se cerro. */
    public void sessionReplaced(HttpServletRequest request, HttpServletResponse response,
                                SesionActivaResponse nueva) throws IOException {
        String donde = nueva.plataforma() == PlataformaSesion.WEB ? "la plataforma web" : "la app movil";
        writeBody(request, response, HttpServletResponse.SC_UNAUTHORIZED, new GenericResponse<>(
                GenericResponse.CODIGO_ERROR, "Tu sesion se cerro porque iniciaste sesion en " + donde, nueva));
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authenticationException) throws IOException {
        writeError(request, response, HttpServletResponse.SC_UNAUTHORIZED,
                "La sesion es invalida, expiro o fue reemplazada por un nuevo inicio de sesion");
    }

    private void writeError(HttpServletRequest request, HttpServletResponse response,
                            int status, String message) throws IOException {
        writeBody(request, response, status, GenericResponse.error(message));
    }

    private void writeBody(HttpServletRequest request, HttpServletResponse response,
                           int status, GenericResponse<?> body) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader("Cache-Control", "no-store");
        objectMapper.writeValue(response.getOutputStream(), (request.getRequestURI().equals("/api/pagos") || request.getRequestURI().startsWith("/api/pagos/"))
                ? com.proyecto.servicios.model.pago.PagoDtos.Resultado.error() : body);
    }
}
