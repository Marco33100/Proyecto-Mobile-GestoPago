package com.proyecto.servicios.exception.auth;

public class LoginRateLimitException extends RuntimeException {
    private final long retryAfterSeconds;

    public LoginRateLimitException(long retryAfterSeconds) {
        super("Demasiados intentos de inicio de sesion. Espera antes de intentar nuevamente.");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
