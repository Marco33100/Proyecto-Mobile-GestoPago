package com.proyecto.servicios.exception.auth;

/** Falta la clave que cifra los secretos TOTP: el inicio de sesion no puede continuar. */
public class SegundoFactorNoDisponibleException extends RuntimeException {
    public SegundoFactorNoDisponibleException() {
        super("El segundo factor no esta disponible");
    }
}
