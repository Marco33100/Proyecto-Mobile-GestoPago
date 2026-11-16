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
    /**
     * Motivos de negocio que el cliente necesita conocer para corregir o decidir. Cualquier
     * otro mensaje interno se sustituye por uno generico segun el estado HTTP.
     */
    private static final java.util.Map<String, String> MOTIVOS = java.util.Map.ofEntries(
            java.util.Map.entry("Saldo disponible insuficiente", "Saldo disponible insuficiente para realizar el pago."),
            java.util.Map.entry("La cuenta esta inactiva", "La cuenta no esta activa para realizar pagos."),
            java.util.Map.entry("Activa el segundo factor antes de pagar", "Activa el segundo factor antes de realizar el pago."),
            java.util.Map.entry("Ya existe una operacion equivalente sin resolver; consulta su estado",
                    "Ya existe un pago similar pendiente. Consulta su estado antes de intentar nuevamente."),
            java.util.Map.entry("Codigo incorrecto, vencido o ya utilizado",
                    "El codigo de autenticacion no es correcto, ya vencio o ya se uso. Espera el siguiente codigo e intentalo de nuevo."),
            java.util.Map.entry("El segundo factor cambio; prepara nuevamente el pago",
                    "Tu autenticador cambio despues de preparar el pago. Preparalo de nuevo."),
            java.util.Map.entry("Se alcanzo el limite diario del cliente", "Alcanzaste el limite diario de pagos. Intentalo de nuevo manana."),
            java.util.Map.entry("El importe supera el limite por operacion", "El importe supera el maximo permitido por operacion."),
            java.util.Map.entry("El monto esta fuera del rango permitido para el producto",
                    "El importe esta fuera del rango permitido para este producto."),
            java.util.Map.entry("El monto debe coincidir con el precio del producto", "El importe debe ser el precio del producto."),
            java.util.Map.entry("El telefono no tiene el formato requerido", "El telefono debe tener entre 10 y 20 digitos."),
            java.util.Map.entry("La referencia es obligatoria y no admite caracteres de control",
                    "Escribe la referencia tal como aparece en tu recibo."),
            java.util.Map.entry("Referencia no valida", "El proveedor indico que la referencia no es valida. Revisala en tu recibo."),
            java.util.Map.entry("La referencia aun no ha sido validada",
                    "La referencia aun no se valida con el proveedor. Consulta el pago en unos momentos."),
            java.util.Map.entry("El importe no corresponde a la deuda y regla confirmadas",
                    "El importe no corresponde a la deuda que informo el proveedor."),
            java.util.Map.entry("Este tipo de producto requiere un contrato adicional del proveedor",
                    "Este producto todavia no se puede pagar desde la app."),
            java.util.Map.entry("Producto no disponible en el catalogo local", "Este producto ya no esta disponible en el catalogo."));

    @ExceptionHandler(PagoException.class)
    public ResponseEntity<Resultado> negocio(PagoException exception) {
        String motivo = exception.getMessage() == null ? null : MOTIVOS.get(exception.getMessage());
        String mensaje = motivo != null ? motivo : switch (exception.getStatus()) {
            case CONFLICT -> "No se puede completar la operacion en su estado actual. Revisa los datos o consulta el pago.";
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
