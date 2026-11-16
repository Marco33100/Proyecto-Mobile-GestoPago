package com.proyecto.servicios.exception.auth;

import com.proyecto.servicios.model.auth.SesionActivaResponse;

/** Ya hay una sesion abierta y el usuario no confirmo cerrarla. El codigo no se consumio. */
public class SesionActivaException extends RuntimeException {
    private final transient SesionActivaResponse sesion;

    public SesionActivaException(SesionActivaResponse sesion) {
        super("Ya existe una sesion activa");
        this.sesion = sesion;
    }

    public SesionActivaResponse getSesion() { return sesion; }
}
