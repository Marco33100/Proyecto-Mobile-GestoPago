package com.proyecto.servicios.exception.cliente;

public class CurpDuplicadaException extends ClienteConflictException {
    public CurpDuplicadaException() {
        super("La CURP ya esta registrada");
    }

}
