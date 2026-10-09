package com.proyecto.servicios.exception.cliente;

public class RfcDuplicadoException extends ClienteConflictException {
    public RfcDuplicadoException() {
        super("El RFC ya esta registrado");
    }

    @Override
    public String getCode() {
        return "DUPLICATE_RFC";
    }
}
