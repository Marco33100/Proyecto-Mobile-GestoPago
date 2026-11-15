package com.proyecto.servicios.exception.auth;

public class InvalidCredentialsException extends RuntimeException {
    public InvalidCredentialsException() {
        super("El correo o la contrasena son incorrectos");
    }
}
