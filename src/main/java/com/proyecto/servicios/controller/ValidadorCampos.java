package com.proyecto.servicios.controller;

import com.proyecto.servicios.model.GenericResponse;
import jakarta.validation.ConstraintViolationException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.validation.Errors;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import java.util.Map;
import java.util.TreeMap;

/** Solo validaciones; los errores de negocio y protocolo permanecen en el advice global. */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class ValidadorCampos {
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<GenericResponse<Void>> dtoValidation(MethodArgumentNotValidException exception) {
        Map<String, String> errors = new TreeMap<>();
        agregarCampos(exception.getBindingResult(), errors);
        return respuesta(errors);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<?> parametrosValidation(HandlerMethodValidationException exception) {
        if (exception.isForReturnValue()) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).contentType(MediaType.APPLICATION_JSON)
                    .header("Cache-Control", "no-store")
                    .body(GenericResponse.error("Ocurrio un error interno"));
        }
        Map<String, String> errors = new TreeMap<>();
        exception.getAllValidationResults().forEach(result -> {
            if (result instanceof ParameterErrors campos) {
                agregarCampos(campos, errors);
            } else {
                String nombre = result.getMethodParameter().getParameterName();
                result.getResolvableErrors().forEach(error -> errors.putIfAbsent(
                        nombre == null ? "parametro" : nombre, mensaje(error.getDefaultMessage())));
            }
        });
        return respuesta(errors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<GenericResponse<Void>> restriccionesValidation(ConstraintViolationException exception) {
        Map<String, String> errors = new TreeMap<>();
        exception.getConstraintViolations().forEach(violation -> {
            String campo = "parametro";
            for (var node : violation.getPropertyPath()) if (node.getName() != null) campo = node.getName();
            errors.putIfAbsent(campo, mensaje(violation.getMessage()));
        });
        return respuesta(errors);
    }

    private void agregarCampos(Errors binding, Map<String, String> errors) {
        binding.getFieldErrors().forEach(error -> {
            String detalle = error.isBindingFailure()
                    ? "El campo no tiene un formato valido" : mensaje(error.getDefaultMessage());
            // Si falta el texto, priorizar el mensaje obligatorio frente a longitud o formato.
            if ("NotBlank".equals(error.getCode())) errors.put(error.getField(), detalle);
            else errors.putIfAbsent(error.getField(), detalle);
        });
        binding.getGlobalErrors().forEach(error -> errors.putIfAbsent("dto", mensaje(error.getDefaultMessage())));
    }

    private String mensaje(String message) {
        return message == null || message.isBlank() ? "El campo no es valido" : message;
    }

    private ResponseEntity<GenericResponse<Void>> respuesta(Map<String, String> errors) {
        return ResponseEntity.badRequest().contentType(MediaType.APPLICATION_JSON)
                .header("Cache-Control", "no-store").body(GenericResponse.error("Datos no validos: " + errors));
    }
}
