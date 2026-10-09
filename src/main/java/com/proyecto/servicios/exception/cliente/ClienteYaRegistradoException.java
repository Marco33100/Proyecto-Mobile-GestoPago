package com.proyecto.servicios.exception.cliente;

public class ClienteYaRegistradoException extends ClienteConflictException {
    public ClienteYaRegistradoException() {
        super("El correo ya pertenece a un cliente registrado");
    }

    @Override
    public String getCode() {
        return "CLIENT_ALREADY_REGISTERED";
    }
}
