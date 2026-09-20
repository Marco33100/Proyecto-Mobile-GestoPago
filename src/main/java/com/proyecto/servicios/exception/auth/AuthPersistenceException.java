package com.proyecto.servicios.exception.auth;

public class AuthPersistenceException extends RuntimeException {
    public AuthPersistenceException(String message, Throwable cause) {
        super(message, cause);
    }
}
