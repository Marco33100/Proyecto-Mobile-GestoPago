package com.proyecto.servicios.controller;

import com.proyecto.servicios.exception.ProductIntegrationException;
import com.proyecto.servicios.exception.auth.*;
import com.proyecto.servicios.exception.cliente.*;
import com.proyecto.servicios.model.GenericResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.transaction.TransactionException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.beans.TypeMismatchException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;


/** Errores HTTP y de negocio centralizados; nunca serializa causas, SQL o valores rechazados. */
@Slf4j
@RestControllerAdvice
public class GlobalApiExceptionHandler extends ResponseEntityExceptionHandler {
    @ExceptionHandler(CuentaNoEncontradaException.class)
    ResponseEntity<Object> cuentaNoEncontrada(HttpServletRequest request) {
        return response(HttpStatus.NOT_FOUND, "No existe la cuenta solicitada", new HttpHeaders());
    }

    @ExceptionHandler(ClienteNoEncontradoException.class)
    ResponseEntity<Object> clienteNoEncontrado(HttpServletRequest request) {
        return response(HttpStatus.NOT_FOUND, "No existe el cliente solicitado", new HttpHeaders());
    }

    @ExceptionHandler(ClienteConflictException.class)
    ResponseEntity<Object> conflictoCliente(ClienteConflictException exception, HttpServletRequest request) {
        return response(HttpStatus.CONFLICT, exception.getMessage(), new HttpHeaders());
    }

    @ExceptionHandler(CuentaConflictException.class)
    ResponseEntity<Object> conflictoCuenta(HttpServletRequest request) {
        return response(HttpStatus.CONFLICT,
                "La reactivacion de cuentas no esta permitida", new HttpHeaders());
    }

    @ExceptionHandler(ClienteValidationException.class)
    ResponseEntity<Object> datosInvalidos(ClienteValidationException exception, HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, exception.getMessage(), new HttpHeaders());
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    ResponseEntity<Object> credencialesInvalidas(HttpServletRequest request) {
        return response(HttpStatus.UNAUTHORIZED,
                "El correo o la contrasena son incorrectos", new HttpHeaders());
    }

    @ExceptionHandler(UsuarioNoEncontradoException.class)
    ResponseEntity<Object> usuarioNoEncontrado(HttpServletRequest request) {
        return esLogin(request) ? credencialesInvalidas(request)
                : response(HttpStatus.NOT_FOUND, "Usuario no encontrado", new HttpHeaders());
    }

    @ExceptionHandler(UsuarioInactivoException.class)
    ResponseEntity<Object> usuarioInactivo(HttpServletRequest request) {
        return esLogin(request) ? credencialesInvalidas(request)
                : response(HttpStatus.FORBIDDEN, "El usuario se encuentra inactivo", new HttpHeaders());
    }

    @ExceptionHandler(ContrasenaInvalidaException.class)
    ResponseEntity<Object> contrasenaInvalida(ContrasenaInvalidaException exception, HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, exception.getMessage(), new HttpHeaders());
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<Object> accesoDenegado(HttpServletRequest request) {
        return response(HttpStatus.FORBIDDEN, "No tienes permisos para realizar esta operacion", new HttpHeaders());
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<Object> sesionInvalida(HttpServletRequest request) {
        return response(HttpStatus.UNAUTHORIZED, "Se requiere una sesion valida", new HttpHeaders());
    }

    @ExceptionHandler(LoginRateLimitException.class)
    ResponseEntity<Object> demasiadosIntentos(LoginRateLimitException exception, HttpServletRequest request) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Retry-After", Long.toString(exception.getRetryAfterSeconds()));
        return response(HttpStatus.TOO_MANY_REQUESTS,
                "Demasiados intentos de inicio de sesion", headers);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ResponseEntity<Object> actualizacionConcurrente(HttpServletRequest request) {
        return response(HttpStatus.CONFLICT,
                "La informacion cambio durante la operacion; consulta nuevamente e intenta otra vez", new HttpHeaders());
    }

    @ExceptionHandler({ClientePersistenceException.class, CuentaPersistenceException.class,
            AuthPersistenceException.class, DataAccessException.class, TransactionException.class})
    ResponseEntity<Object> persistencia(RuntimeException exception, HttpServletRequest request) {
        log.error("Error de persistencia: {}", exception.getClass().getSimpleName());
        String message = esAuth(request) || esCuenta(request) ? "No fue posible completar la operacion"
                : "No fue posible completar la operacion de clientes o cuentas";
        return response(HttpStatus.SERVICE_UNAVAILABLE, message, new HttpHeaders());
    }

    @ExceptionHandler(ProductIntegrationException.class)
    ResponseEntity<Object> integracion(ProductIntegrationException exception, HttpServletRequest request) {
        // El mensaje de una integracion puede contener una URL o una respuesta del proveedor.
        return switch (exception.getErrorType()) {
            case GESTOPAGO_UNAVAILABLE -> response(HttpStatus.BAD_GATEWAY,
                    "El proveedor de productos no esta disponible", new HttpHeaders());
            case DATABASE_ERROR -> response(HttpStatus.INTERNAL_SERVER_ERROR,
                    "No fue posible procesar el catalogo de productos", new HttpHeaders());
        };
    }

    @Override
    protected ResponseEntity<Object> handleTypeMismatch(TypeMismatchException exception,
            HttpHeaders headers, HttpStatusCode status, WebRequest webRequest) {
        String name = exception instanceof MethodArgumentTypeMismatchException parameter ? parameter.getName() : "parametro";
        return response(HttpStatus.BAD_REQUEST,
                "El parametro " + name + " no tiene un formato valido", headers);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException exception,
            HttpHeaders headers, HttpStatusCode status, WebRequest webRequest) {
        return response(HttpStatus.BAD_REQUEST,
                "El cuerpo de la solicitud no tiene un formato valido o contiene campos no permitidos", headers);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception exception, Object body,
            HttpHeaders headers, HttpStatusCode status, WebRequest webRequest) {
        String message = switch (status.value()) {
            case 400 -> "La solicitud contiene parametros invalidos o faltantes";
            case 404 -> "No existe el recurso solicitado";
            case 405 -> "El metodo HTTP no esta permitido para este recurso";
            case 406 -> "El formato de respuesta solicitado no esta disponible";
            case 415 -> "El tipo de contenido de la solicitud no esta permitido";
            default -> "Ocurrio un error interno";
        };
        return response(status, message, headers);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Object> inesperado(Exception exception, HttpServletRequest request) {
        log.error("Error interno: {}", exception.getClass().getSimpleName());
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "Ocurrio un error interno", new HttpHeaders());
    }

    private boolean esLogin(HttpServletRequest request) { return request.getRequestURI().endsWith("/auth/login"); }
    private boolean esAuth(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/api/auth") || request.getRequestURI().startsWith("/api/usuarios");
    }
    private boolean esCuenta(HttpServletRequest request) { return request.getRequestURI().startsWith("/api/cuentas"); }

    private ResponseEntity<Object> response(HttpStatusCode status, String message, HttpHeaders headers) {
        HttpHeaders safeHeaders = new HttpHeaders();
        safeHeaders.putAll(headers); // Conserva Allow, Accept y Retry-After.
        safeHeaders.setContentType(MediaType.APPLICATION_JSON);
        safeHeaders.setCacheControl("no-store");
        return new ResponseEntity<>(GenericResponse.error(message), safeHeaders, status);
    }
}
