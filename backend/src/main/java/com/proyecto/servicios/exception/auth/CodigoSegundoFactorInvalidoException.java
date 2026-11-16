package com.proyecto.servicios.exception.auth;

/** Codigo del autenticador incorrecto, ya usado, o desafio de inicio de sesion vencido. */
public class CodigoSegundoFactorInvalidoException extends RuntimeException {
    public CodigoSegundoFactorInvalidoException() {
        super("El codigo no es correcto o la verificacion vencio");
    }
}
