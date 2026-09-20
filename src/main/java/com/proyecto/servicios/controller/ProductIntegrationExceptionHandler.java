package com.proyecto.servicios.controller;

import com.proyecto.servicios.exception.ProductIntegrationErrorType;
import com.proyecto.servicios.exception.ProductIntegrationException;
import com.proyecto.servicios.model.product.IntegrationErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import lombok.extern.slf4j.Slf4j;
import jakarta.servlet.http.HttpServletRequest;

import java.time.Instant;

@Slf4j
@RestControllerAdvice(assignableTypes = ProductCatalogController.class)
public class ProductIntegrationExceptionHandler {

    @ExceptionHandler(ProductIntegrationException.class)
    public ResponseEntity<IntegrationErrorResponse> handleProductIntegrationException(
            ProductIntegrationException exception,
            HttpServletRequest request
    ) {
        HttpStatus status = mapStatus(exception.getErrorType());
        IntegrationErrorResponse response = new IntegrationErrorResponse(
                Instant.now(),
                exception.getErrorType().getCode(),
                exception.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(status).body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<IntegrationErrorResponse> handleUnexpectedException(
            Exception exception,
            HttpServletRequest request
    ) {
        log.error("Error no controlado al consultar el catálogo de productos", exception);
        IntegrationErrorResponse response = new IntegrationErrorResponse(
                Instant.now(),
                ProductIntegrationErrorType.DATABASE_ERROR.getCode(),
                "Ocurrió un error interno al procesar el catálogo de productos",
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    private HttpStatus mapStatus(ProductIntegrationErrorType errorType) {
        return switch (errorType) {
            case GESTOPAGO_UNAVAILABLE -> HttpStatus.BAD_GATEWAY;
            case DATABASE_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
