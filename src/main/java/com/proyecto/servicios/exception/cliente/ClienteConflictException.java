package com.proyecto.servicios.exception.cliente;

public class ClienteConflictException extends RuntimeException {

    public ClienteConflictException(String message) {
        super(message);
    }

    public String getCode() {
        return "CLIENT_DATA_CONFLICT";
    }
}
