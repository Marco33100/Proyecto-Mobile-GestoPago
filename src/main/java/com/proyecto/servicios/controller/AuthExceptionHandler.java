package com.proyecto.servicios.controller;

import com.proyecto.servicios.exception.auth.AuthPersistenceException;
import com.proyecto.servicios.exception.auth.InvalidCredentialsException;
import com.proyecto.servicios.exception.auth.RegistrationException;
import com.proyecto.servicios.exception.auth.UserAlreadyExistsException;
import com.proyecto.servicios.model.auth.AuthErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
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
@RestControllerAdvice(assignableTypes = AuthController.class)
public class AuthExceptionHandler {

    @ExceptionHandler(UserAlreadyExistsException.class)
    public ResponseEntity<AuthErrorResponse> handleConflict(UserAlreadyExistsException exception) {
        return response(HttpStatus.CONFLICT, "AUTH_USER_EXISTS", exception.getMessage(), Map.of());
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<AuthErrorResponse> handleInvalidCredentials(InvalidCredentialsException exception) {
        return response(HttpStatus.UNAUTHORIZED, "AUTH_INVALID_CREDENTIALS", exception.getMessage(), Map.of());
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

    @ExceptionHandler({RegistrationException.class, AuthPersistenceException.class})
    public ResponseEntity<AuthErrorResponse> handlePersistence(RuntimeException exception) {
        log.error("Error de persistencia en autenticacion", exception);
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "AUTH_DATABASE_ERROR",
                "No fue posible completar la operacion", Map.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<AuthErrorResponse> handleUnexpected(Exception exception) {
        log.error("Error no controlado en autenticacion", exception);
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "AUTH_INTERNAL_ERROR",
                "Ocurrio un error interno", Map.of());
    }

    private ResponseEntity<AuthErrorResponse> response(
            HttpStatus status, String code, String message, Map<String, String> fieldErrors
    ) {
        return ResponseEntity.status(status).body(new AuthErrorResponse(
                Instant.now(), status.value(), code, message, fieldErrors
        ));
    }
}
