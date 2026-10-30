package com.proyecto.servicios.controller;

import com.proyecto.servicios.exception.PagoException;
import com.proyecto.servicios.model.pago.PagoDtos.Resultado;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;

@RestControllerAdvice(assignableTypes = {PagoController.class, ConciliacionPagoController.class})
@Order(Ordered.HIGHEST_PRECEDENCE)
public class PagoExceptionHandler {
    @ExceptionHandler(PagoException.class)
    public ResponseEntity<Resultado> negocio(PagoException exception) { return error(exception.getStatus()); }
    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<Resultado> validacion(Exception exception) { return error(HttpStatus.BAD_REQUEST); }
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Resultado> prohibido() { return error(HttpStatus.FORBIDDEN); }
    @ExceptionHandler({org.springframework.dao.DataIntegrityViolationException.class,
            org.springframework.orm.ObjectOptimisticLockingFailureException.class})
    public ResponseEntity<Resultado> conflicto(Exception exception) { return error(HttpStatus.CONFLICT); }
    @ExceptionHandler(com.proyecto.servicios.exception.auth.LoginRateLimitException.class)
    public ResponseEntity<Resultado> limite(com.proyecto.servicios.exception.auth.LoginRateLimitException exception) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).header("Retry-After", Long.toString(exception.getRetryAfterSeconds()))
                .header("Cache-Control", "no-store").body(Resultado.error());
    }
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Resultado> inesperado(Exception exception) { return error(HttpStatus.SERVICE_UNAVAILABLE); }
    private ResponseEntity<Resultado> error(HttpStatus status) {
        return ResponseEntity.status(status).header("Cache-Control", "no-store").body(Resultado.error());
    }
}
