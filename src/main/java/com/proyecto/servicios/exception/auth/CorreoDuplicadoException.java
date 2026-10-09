package com.proyecto.servicios.exception.auth;

public class CorreoDuplicadoException extends UserAlreadyExistsException {
    public CorreoDuplicadoException() {
        super("El correo electronico ya esta registrado");
    }
}
