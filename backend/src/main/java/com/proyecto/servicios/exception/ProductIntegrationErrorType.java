package com.proyecto.servicios.exception;

public enum ProductIntegrationErrorType {
    GESTOPAGO_UNAVAILABLE(1),
    DATABASE_ERROR(2);

    private final int code;

    ProductIntegrationErrorType(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
