package com.proyecto.servicios.controller;

import com.proyecto.servicios.exception.ProductIntegrationException;
import com.proyecto.servicios.exception.auth.*;
import com.proyecto.servicios.exception.cliente.*;
import com.proyecto.servicios.model.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.transaction.TransactionException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.beans.TypeMismatchException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** Errores HTTP y de negocio centralizados; nunca serializa causas, SQL o valores rechazados. */
@Slf4j
@RestControllerAdvice
public class GlobalApiExceptionHandler extends ResponseEntityExceptionHandler {
    @ExceptionHandler(CuentaNoEncontradaException.class)
    ResponseEntity<Object> cuentaNoEncontrada(HttpServletRequest request) {
        return response(HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND", "No existe la cuenta solicitada", request, Map.of());
    }

    @ExceptionHandler(ClienteNoEncontradoException.class)
    ResponseEntity<Object> clienteNoEncontrado(HttpServletRequest request) {
        return response(HttpStatus.NOT_FOUND, "CLIENT_NOT_FOUND", "No existe el cliente solicitado", request, Map.of());
    }

    @ExceptionHandler(ClienteConflictException.class)
    ResponseEntity<Object> conflictoCliente(ClienteConflictException exception, HttpServletRequest request) {
        return response(HttpStatus.CONFLICT, exception.getCode(), exception.getMessage(), request, Map.of());
    }

    @ExceptionHandler(CuentaConflictException.class)
    ResponseEntity<Object> conflictoCuenta(HttpServletRequest request) {
        return response(HttpStatus.CONFLICT, "ACCOUNT_STATE_CONFLICT",
                "La reactivacion de cuentas no esta permitida", request, Map.of());
    }

    @ExceptionHandler(ClienteValidationException.class)
    ResponseEntity<Object> datosInvalidos(ClienteValidationException exception, HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, validationCode(request), exception.getMessage(), request, Map.of());
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<Object> restriccionesInvalidas(ConstraintViolationException exception, HttpServletRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        exception.getConstraintViolations().forEach(violation -> {
            String field = "parametro";
            for (var node : violation.getPropertyPath()) {
                field = node.getName();
            }
            errors.putIfAbsent(field, violation.getMessage());
        });
        return response(HttpStatus.BAD_REQUEST, validationCode(request),
                "La solicitud contiene datos invalidos", request, errors);
    }

    @ExceptionHandler(UserAlreadyExistsException.class)
    ResponseEntity<Object> usuarioDuplicado(UserAlreadyExistsException exception, HttpServletRequest request) {
        return response(HttpStatus.CONFLICT, exception instanceof CorreoDuplicadoException
                ? "AUTH_DUPLICATE_EMAIL" : "AUTH_USER_EXISTS", exception.getMessage(), request, Map.of());
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    ResponseEntity<Object> credencialesInvalidas(HttpServletRequest request) {
        return response(HttpStatus.UNAUTHORIZED, "AUTH_INVALID_CREDENTIALS",
                "El correo o la contrasena son incorrectos", request, Map.of());
    }

    @ExceptionHandler(UsuarioNoEncontradoException.class)
    ResponseEntity<Object> usuarioNoEncontrado(HttpServletRequest request) {
        return esLogin(request) ? credencialesInvalidas(request)
                : response(HttpStatus.NOT_FOUND, "AUTH_USER_NOT_FOUND", "Usuario no encontrado", request, Map.of());
    }

    @ExceptionHandler(UsuarioInactivoException.class)
    ResponseEntity<Object> usuarioInactivo(HttpServletRequest request) {
        return esLogin(request) ? credencialesInvalidas(request)
                : response(HttpStatus.FORBIDDEN, "AUTH_USER_INACTIVE", "El usuario se encuentra inactivo", request, Map.of());
    }

    @ExceptionHandler(ContrasenaInvalidaException.class)
    ResponseEntity<Object> contrasenaInvalida(ContrasenaInvalidaException exception, HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, "AUTH_INVALID_PASSWORD", exception.getMessage(), request, Map.of());
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<Object> accesoDenegado(HttpServletRequest request) {
        return response(HttpStatus.FORBIDDEN, "AUTH_FORBIDDEN", "No tienes permisos para realizar esta operacion", request, Map.of());
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<Object> sesionInvalida(HttpServletRequest request) {
        return response(HttpStatus.UNAUTHORIZED, "AUTH_INVALID_SESSION", "Se requiere una sesion valida", request, Map.of());
    }

    @ExceptionHandler(LoginRateLimitException.class)
    ResponseEntity<Object> demasiadosIntentos(LoginRateLimitException exception, HttpServletRequest request) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Retry-After", Long.toString(exception.getRetryAfterSeconds()));
        return response(HttpStatus.TOO_MANY_REQUESTS, "AUTH_TOO_MANY_ATTEMPTS",
                "Demasiados intentos de inicio de sesion", request, Map.of(), headers);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ResponseEntity<Object> actualizacionConcurrente(HttpServletRequest request) {
        return response(HttpStatus.CONFLICT, esCuenta(request) ? "ACCOUNT_CONCURRENT_UPDATE" : "CLIENT_CONCURRENT_UPDATE",
                "La informacion cambio durante la operacion; consulta nuevamente e intenta otra vez", request, Map.of());
    }

    @ExceptionHandler({ClientePersistenceException.class, CuentaPersistenceException.class,
            AuthPersistenceException.class, RegistrationException.class, DataAccessException.class, TransactionException.class})
    ResponseEntity<Object> persistencia(RuntimeException exception, HttpServletRequest request) {
        log.error("Error de persistencia: {}", exception.getClass().getSimpleName());
        String code = esAuth(request) ? "AUTH_DATABASE_ERROR"
                : esCuenta(request) ? "ACCOUNT_DATABASE_ERROR" : "CLIENT_DATABASE_ERROR";
        String message = esAuth(request) || esCuenta(request) ? "No fue posible completar la operacion"
                : "No fue posible completar la operacion de clientes o cuentas";
        return response(HttpStatus.SERVICE_UNAVAILABLE, code, message, request, Map.of());
    }

    @ExceptionHandler(ProductIntegrationException.class)
    ResponseEntity<Object> integracion(ProductIntegrationException exception, HttpServletRequest request) {
        // El mensaje de una integracion puede contener una URL o una respuesta del proveedor.
        return switch (exception.getErrorType()) {
            case GESTOPAGO_UNAVAILABLE -> response(HttpStatus.BAD_GATEWAY, "GESTOPAGO_UNAVAILABLE",
                    "El proveedor de productos no esta disponible", request, Map.of());
            case DATABASE_ERROR -> response(HttpStatus.INTERNAL_SERVER_ERROR, "PRODUCT_DATABASE_ERROR",
                    "No fue posible procesar el catalogo de productos", request, Map.of());
        };
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException exception,
            HttpHeaders headers, HttpStatusCode status, WebRequest webRequest) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError error : exception.getBindingResult().getFieldErrors()) {
            errors.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        return response(HttpStatus.BAD_REQUEST, validationCode(request(webRequest)),
                "La solicitud contiene datos invalidos", request(webRequest), errors, headers);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException exception,
            HttpHeaders headers, HttpStatusCode status, WebRequest webRequest) {
        if (exception.isForReturnValue()) {
            return handleExceptionInternal(exception, null, headers, HttpStatus.INTERNAL_SERVER_ERROR, webRequest);
        }
        Map<String, String> errors = new LinkedHashMap<>();
        exception.getAllValidationResults().forEach(result -> {
            String name = result.getMethodParameter().getParameterName();
            result.getResolvableErrors().forEach(error -> errors.putIfAbsent(
                    name == null ? "parametro" : name, error.getDefaultMessage()));
        });
        return response(HttpStatus.BAD_REQUEST, validationCode(request(webRequest)),
                "La solicitud contiene datos invalidos", request(webRequest), errors, headers);
    }

    @Override
    protected ResponseEntity<Object> handleTypeMismatch(TypeMismatchException exception,
            HttpHeaders headers, HttpStatusCode status, WebRequest webRequest) {
        String name = exception instanceof MethodArgumentTypeMismatchException parameter ? parameter.getName() : "parametro";
        return response(HttpStatus.BAD_REQUEST, esAuth(request(webRequest)) ? "AUTH_INVALID_PARAMETER" : "INVALID_QUERY_PARAMETER",
                "El parametro " + name + " no tiene un formato valido", request(webRequest), Map.of(), headers);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException exception,
            HttpHeaders headers, HttpStatusCode status, WebRequest webRequest) {
        return response(HttpStatus.BAD_REQUEST, esAuth(request(webRequest)) ? "AUTH_INVALID_JSON" : "INVALID_REQUEST_BODY",
                "El cuerpo de la solicitud no tiene un formato valido o contiene campos no permitidos",
                request(webRequest), Map.of(), headers);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception exception, Object body,
            HttpHeaders headers, HttpStatusCode status, WebRequest webRequest) {
        String code = switch (status.value()) {
            case 400 -> "INVALID_REQUEST";
            case 404 -> "RESOURCE_NOT_FOUND";
            case 405 -> "METHOD_NOT_ALLOWED";
            case 406 -> "NOT_ACCEPTABLE";
            case 415 -> "UNSUPPORTED_MEDIA_TYPE";
            default -> "INTERNAL_ERROR";
        };
        String message = switch (status.value()) {
            case 400 -> "La solicitud contiene parametros invalidos o faltantes";
            case 404 -> "No existe el recurso solicitado";
            case 405 -> "El metodo HTTP no esta permitido para este recurso";
            case 406 -> "El formato de respuesta solicitado no esta disponible";
            case 415 -> "El tipo de contenido de la solicitud no esta permitido";
            default -> "Ocurrio un error interno";
        };
        return response(status, code, message, request(webRequest), Map.of(), headers);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Object> inesperado(Exception exception, HttpServletRequest request) {
        log.error("Error interno: {}", exception.getClass().getSimpleName());
        String code = esAuth(request) ? "AUTH_INTERNAL_ERROR" : esCuenta(request)
                ? "ACCOUNT_INTERNAL_ERROR" : request.getRequestURI().startsWith("/api/clientes") ? "CLIENT_INTERNAL_ERROR" : "INTERNAL_ERROR";
        return response(HttpStatus.INTERNAL_SERVER_ERROR, code, "Ocurrio un error interno", request, Map.of());
    }

    private String validationCode(HttpServletRequest request) {
        return esAuth(request) ? "AUTH_INVALID_PAYLOAD" : esCuenta(request) ? "INVALID_ACCOUNT_DATA" : "INVALID_CLIENT_DATA";
    }

    private boolean esLogin(HttpServletRequest request) { return request.getRequestURI().endsWith("/auth/login"); }
    private boolean esAuth(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/api/auth") || request.getRequestURI().startsWith("/api/usuarios");
    }
    private boolean esCuenta(HttpServletRequest request) { return request.getRequestURI().startsWith("/api/cuentas"); }
    private HttpServletRequest request(WebRequest request) { return ((ServletWebRequest) request).getRequest(); }

    private ResponseEntity<Object> response(HttpStatusCode status, String code, String message,
            HttpServletRequest request, Map<String, String> errors) {
        return response(status, code, message, request, errors, new HttpHeaders());
    }

    private ResponseEntity<Object> response(HttpStatusCode status, String code, String message,
            HttpServletRequest request, Map<String, String> errors, HttpHeaders headers) {
        HttpHeaders safeHeaders = new HttpHeaders();
        safeHeaders.putAll(headers); // Conserva Allow, Accept y Retry-After cuando aplican.
        safeHeaders.setContentType(MediaType.APPLICATION_JSON);
        safeHeaders.setCacheControl("no-store");
        return new ResponseEntity<>(new ApiErrorResponse(Instant.now(), status.value(), code, message,
                request.getRequestURI(), errors), safeHeaders, status);
    }
}
