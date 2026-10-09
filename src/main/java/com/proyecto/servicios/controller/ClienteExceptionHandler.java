package com.proyecto.servicios.controller;

import com.proyecto.servicios.exception.cliente.ClienteConflictException;
import com.proyecto.servicios.exception.cliente.ClienteNoEncontradoException;
import com.proyecto.servicios.exception.cliente.ClientePersistenceException;
import com.proyecto.servicios.exception.cliente.ClienteValidationException;
import com.proyecto.servicios.exception.cliente.CuentaPersistenceException;
import com.proyecto.servicios.exception.cliente.CuentaNoEncontradaException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import com.proyecto.servicios.model.cliente.ClienteErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.TransactionException;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice(assignableTypes = {ClienteController.class, CuentaController.class})
public class ClienteExceptionHandler {

    @ExceptionHandler(CuentaNoEncontradaException.class)
    public ResponseEntity<ClienteErrorResponse> handleAccountNotFound(
            CuentaNoEncontradaException exception, HttpServletRequest request) {
        return response(HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND", exception.getMessage(), request, Map.of());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ClienteErrorResponse> handleInvalidParameter(
            MethodArgumentTypeMismatchException exception, HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, "INVALID_QUERY_PARAMETER",
                "El parametro " + exception.getName() + " no tiene un formato valido", request, Map.of());
    }

    @ExceptionHandler(ClienteNoEncontradoException.class)
    public ResponseEntity<ClienteErrorResponse> handleNotFound(
            ClienteNoEncontradoException exception,
            HttpServletRequest request
    ) {
        return response(HttpStatus.NOT_FOUND, "CLIENT_NOT_FOUND", exception.getMessage(), request, Map.of());
    }

    @ExceptionHandler(ClienteConflictException.class)
    public ResponseEntity<ClienteErrorResponse> handleConflict(
            ClienteConflictException exception,
            HttpServletRequest request
    ) {
        return response(HttpStatus.CONFLICT, exception.getCode(), exception.getMessage(), request, Map.of());
    }

    @ExceptionHandler({ClienteValidationException.class, ConstraintViolationException.class})
    public ResponseEntity<ClienteErrorResponse> handleValidation(
            RuntimeException exception,
            HttpServletRequest request
    ) {
        return response(HttpStatus.BAD_REQUEST, "INVALID_CLIENT_DATA", exception.getMessage(), request, Map.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ClienteErrorResponse> handleBeanValidation(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError error : exception.getBindingResult().getFieldErrors()) {
            errors.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        return response(
                HttpStatus.BAD_REQUEST,
                "INVALID_CLIENT_DATA",
                "La solicitud contiene datos invalidos",
                request,
                errors
        );
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ClienteErrorResponse> handleUnreadable(
            HttpMessageNotReadableException exception,
            HttpServletRequest request
    ) {
        return response(
                HttpStatus.BAD_REQUEST,
                "INVALID_REQUEST_BODY",
                "El cuerpo de la solicitud no tiene un formato valido",
                request,
                Map.of()
        );
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ClienteErrorResponse> handleConcurrentUpdate(
            OptimisticLockingFailureException exception, HttpServletRequest request) {
        return response(HttpStatus.CONFLICT, "CLIENT_CONCURRENT_UPDATE",
                "La informacion cambio durante la operacion; consulta nuevamente e intenta otra vez", request, Map.of());
    }

    @ExceptionHandler({ClientePersistenceException.class, CuentaPersistenceException.class,
            DataAccessException.class, TransactionException.class})
    public ResponseEntity<ClienteErrorResponse> handlePersistence(
            RuntimeException exception,
            HttpServletRequest request
    ) {
        log.error("Error de persistencia de clientes o cuentas: {}", exception.getClass().getSimpleName());
        return response(
                HttpStatus.SERVICE_UNAVAILABLE,
                "CLIENT_DATABASE_ERROR",
                "No fue posible completar la operacion de clientes o cuentas",
                request,
                Map.of()
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ClienteErrorResponse> handleUnexpected(Exception exception, HttpServletRequest request) {
        log.error("Error interno de clientes o cuentas: {}", exception.getClass().getSimpleName());
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "CLIENT_INTERNAL_ERROR",
                "Ocurrio un error interno", request, Map.of());
    }

    private ResponseEntity<ClienteErrorResponse> response(
            HttpStatus status,
            String code,
            String message,
            HttpServletRequest request,
            Map<String, String> validationErrors
    ) {
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(new ClienteErrorResponse(
                Instant.now(),
                status.value(),
                code,
                message,
                request.getRequestURI(),
                validationErrors
        ));
    }
}
