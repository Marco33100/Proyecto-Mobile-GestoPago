package com.proyecto.servicios.exception.auth;

public class ContrasenaInvalidaException extends RuntimeException {
    public ContrasenaInvalidaException(String message) {
        super(message);
    }
}
