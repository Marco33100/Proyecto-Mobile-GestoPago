package com.proyecto.servicios.exception;

import org.springframework.http.HttpStatus;

/** Only controlled public validation messages; provider diagnostics never reach the client. */
public class PagoException extends RuntimeException {
    private final HttpStatus status;
    public PagoException(HttpStatus status, String mensaje) { super(mensaje); this.status = status; }
    public HttpStatus getStatus() { return status; }
}
