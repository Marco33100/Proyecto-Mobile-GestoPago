package com.proyecto.servicios.model.auth;

/** Token CSRF del cliente web y el encabezado en el que debe enviarlo. */
public record CsrfTokenResponse(String headerName, String token) {
}
