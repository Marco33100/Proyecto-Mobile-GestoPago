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
    public ResponseEntity<Resultado> negocio(PagoException exception) {
        String mensaje = switch (exception.getStatus()) {
            case CONFLICT -> switch (exception.getMessage() == null ? "" : exception.getMessage()) {
                case "Saldo disponible insuficiente" -> "Saldo disponible insuficiente para realizar el pago.";
                case "La cuenta esta inactiva" -> "La cuenta no esta activa para realizar pagos.";
                case "Activa el segundo factor antes de pagar" -> "Activa el segundo factor antes de realizar el pago.";
                case "Ya existe una operacion equivalente sin resolver; consulta su estado" ->
                        "Ya existe un pago similar pendiente. Consulta su estado antes de intentar nuevamente.";
                default -> "No se puede completar la operacion en su estado actual. Revisa los datos o consulta el pago.";
            };
            case BAD_REQUEST, UNPROCESSABLE_ENTITY -> "Los datos enviados no son validos. Revisa la solicitud.";
            case NOT_FOUND -> "No se encontro el recurso solicitado o no esta disponible.";
            case FORBIDDEN -> "No fue posible autorizar la operacion. Verifica tus permisos y datos de autenticacion.";
            case TOO_MANY_REQUESTS -> "Demasiados intentos. Espera antes de volver a intentarlo.";
            default -> "No es posible procesar la solicitud en este momento. Intentalo mas tarde.";
        };
        return error(exception.getStatus(), mensaje);
    }
    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<Resultado> validacion(Exception exception) { return error(HttpStatus.BAD_REQUEST, "Los datos enviados no son validos. Revisa la solicitud."); }
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Resultado> prohibido() { return error(HttpStatus.FORBIDDEN, "No tienes permiso para realizar esta operacion."); }
    @ExceptionHandler({org.springframework.dao.DataIntegrityViolationException.class,
            org.springframework.orm.ObjectOptimisticLockingFailureException.class})
    public ResponseEntity<Resultado> conflicto(Exception exception) { return error(HttpStatus.CONFLICT, "No se puede completar la operacion en su estado actual. Consulta su estado."); }
    @ExceptionHandler(com.proyecto.servicios.exception.auth.LoginRateLimitException.class)
    public ResponseEntity<Resultado> limite(com.proyecto.servicios.exception.auth.LoginRateLimitException exception) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).header("Retry-After", Long.toString(exception.getRetryAfterSeconds()))
                .header("Cache-Control", "no-store").body(Resultado.error("Demasiados intentos. Espera antes de volver a intentarlo."));
    }
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Resultado> inesperado(Exception exception) { return error(HttpStatus.SERVICE_UNAVAILABLE, "No es posible procesar la solicitud en este momento. Intentalo mas tarde."); }
    private ResponseEntity<Resultado> error(HttpStatus status, String mensaje) {
        return ResponseEntity.status(status).header("Cache-Control", "no-store").body(Resultado.error(mensaje));
    }
}
