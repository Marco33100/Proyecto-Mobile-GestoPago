package com.proyecto.servicios.exception.cliente;

public class CuentaConflictException extends RuntimeException {
    public CuentaConflictException(String message) {
        super(message);
    }
}
