package com.proyecto.servicios.exception;

import lombok.Getter;

@Getter
public class ProductIntegrationException extends RuntimeException {

    private final ProductIntegrationErrorType errorType;

    public ProductIntegrationException(ProductIntegrationErrorType errorType, String message) {
        super(message);
        this.errorType = errorType;
    }

    public ProductIntegrationException(
            ProductIntegrationErrorType errorType,
            String message,
            Throwable cause
    ) {
        super(message, cause);
        this.errorType = errorType;
    }
}
