package com.proyecto.servicios.controller;

import com.proyecto.servicios.exception.auth.AuthPersistenceException;
import com.proyecto.servicios.exception.auth.InvalidCredentialsException;
import com.proyecto.servicios.exception.auth.RegistrationException;
import com.proyecto.servicios.exception.auth.UserAlreadyExistsException;
import com.proyecto.servicios.exception.auth.UsuarioNoEncontradoException;
import com.proyecto.servicios.exception.auth.UsuarioInactivoException;
import com.proyecto.servicios.exception.auth.ContrasenaInvalidaException;
import com.proyecto.servicios.exception.auth.CorreoDuplicadoException;
import com.proyecto.servicios.exception.auth.LoginRateLimitException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.TransactionException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import com.proyecto.servicios.model.auth.AuthErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice(assignableTypes = {AuthController.class, UsuarioController.class})
public class AuthExceptionHandler {

    @ExceptionHandler(UserAlreadyExistsException.class)
    public ResponseEntity<AuthErrorResponse> handleConflict(UserAlreadyExistsException exception) {
        return response(HttpStatus.CONFLICT, exception instanceof CorreoDuplicadoException
                ? "AUTH_DUPLICATE_EMAIL" : "AUTH_USER_EXISTS", exception.getMessage(), Map.of());
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<AuthErrorResponse> handleInvalidCredentials(InvalidCredentialsException exception) {
        return invalidCredentials();
    }

    @ExceptionHandler(UsuarioNoEncontradoException.class)
    public ResponseEntity<AuthErrorResponse> handleUserNotFound(UsuarioNoEncontradoException exception,
                                                               HttpServletRequest request) {
        return esLogin(request) ? invalidCredentials()
                : response(HttpStatus.NOT_FOUND, "AUTH_USER_NOT_FOUND", "Usuario no encontrado", Map.of());
    }

    @ExceptionHandler(UsuarioInactivoException.class)
    public ResponseEntity<AuthErrorResponse> handleInactive(UsuarioInactivoException exception,
                                                           HttpServletRequest request) {
        return esLogin(request) ? invalidCredentials()
                : response(HttpStatus.FORBIDDEN, "AUTH_USER_INACTIVE", "El usuario se encuentra inactivo", Map.of());
    }

    @ExceptionHandler(ContrasenaInvalidaException.class)
    public ResponseEntity<AuthErrorResponse> handlePassword(ContrasenaInvalidaException exception) {
        return response(HttpStatus.BAD_REQUEST, "AUTH_INVALID_PASSWORD", exception.getMessage(), Map.of());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<AuthErrorResponse> handleForbidden(AccessDeniedException exception) {
        return response(HttpStatus.FORBIDDEN, "AUTH_FORBIDDEN", "No tienes permiso para acceder a este usuario", Map.of());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<AuthErrorResponse> handleParameter(MethodArgumentTypeMismatchException exception) {
        return response(HttpStatus.BAD_REQUEST, "AUTH_INVALID_PARAMETER", "El identificador no tiene un formato valido", Map.of());
    }

    private boolean esLogin(HttpServletRequest request) {
        return request.getRequestURI().endsWith("/auth/login");
    }

    private ResponseEntity<AuthErrorResponse> invalidCredentials() {
        return response(HttpStatus.UNAUTHORIZED, "AUTH_INVALID_CREDENTIALS",
                "El correo o la contrasena son incorrectos", Map.of());
    }

    @ExceptionHandler(LoginRateLimitException.class)
    public ResponseEntity<AuthErrorResponse> handleRateLimit(LoginRateLimitException exception) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", Long.toString(exception.getRetryAfterSeconds()))
                .contentType(MediaType.APPLICATION_JSON)
                .body(new AuthErrorResponse(Instant.now(), 429, "AUTH_TOO_MANY_ATTEMPTS",
                        exception.getMessage(), Map.of()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<AuthErrorResponse> handleValidation(MethodArgumentNotValidException exception) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError fieldError : exception.getBindingResult().getFieldErrors()) {
            errors.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
        }
        return response(HttpStatus.BAD_REQUEST, "AUTH_INVALID_PAYLOAD",
                "Los datos enviados no son validos", errors);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<AuthErrorResponse> handleMalformedJson(HttpMessageNotReadableException exception) {
        return response(HttpStatus.BAD_REQUEST, "AUTH_INVALID_JSON",
                "El cuerpo de la solicitud no contiene un JSON valido", Map.of());
    }

    @ExceptionHandler({RegistrationException.class, AuthPersistenceException.class,
            DataAccessException.class, TransactionException.class})
    public ResponseEntity<AuthErrorResponse> handlePersistence(RuntimeException exception) {
        log.error("Error de persistencia en autenticacion: {}", exception.getClass().getSimpleName());
        return response(HttpStatus.SERVICE_UNAVAILABLE, "AUTH_DATABASE_ERROR",
                "No fue posible completar la operacion", Map.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<AuthErrorResponse> handleUnexpected(Exception exception) {
        log.error("Error no controlado en autenticacion: {}", exception.getClass().getSimpleName());
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "AUTH_INTERNAL_ERROR",
                "Ocurrio un error interno", Map.of());
    }

    private ResponseEntity<AuthErrorResponse> response(
            HttpStatus status, String code, String message, Map<String, String> fieldErrors
    ) {
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(new AuthErrorResponse(
                Instant.now(), status.value(), code, message, fieldErrors
        ));
    }
}
