package com.proyecto.servicios.model;

import com.fasterxml.jackson.annotation.JsonAnySetter;

/** Los requests tienen una lista cerrada de campos, sin afectar los DTOs del proveedor externo. */
public interface SolicitudJson {
    @JsonAnySetter
    default void rechazarCampoDesconocido(String campo, Object valor) {
        throw new IllegalArgumentException("La solicitud contiene campos no permitidos");
    }
}
