package com.proyecto.servicios.service.Impl;

import com.proyecto.servicios.config.LoginRateLimitProperties;
import com.proyecto.servicios.exception.auth.LoginRateLimitException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Limites locales por instancia; memoria acotada, sin passwords ni tokens. */
@Component
public class LoginAttemptLimiter {
    private final LoginRateLimitProperties properties;
    private final Clock clock;
    private final Map<String, Window> windows = new HashMap<>();
    private Instant nextCleanup = Instant.MIN;

    @Autowired
    public LoginAttemptLimiter(LoginRateLimitProperties properties) {
        this(properties, Clock.systemUTC());
    }

    public LoginAttemptLimiter(LoginRateLimitProperties properties, Clock clock) {
        if (properties.getAddressAttempts() < 1 || properties.getAccountAttempts() < 1
                || properties.getMaxTrackedKeys() < 1
                || properties.getAddressWindow().compareTo(Duration.ofSeconds(1)) < 0
                || properties.getAccountWindow().compareTo(Duration.ofSeconds(1)) < 0) {
            throw new IllegalArgumentException("Los limites y ventanas de login deben ser positivos");
        }
        this.properties = properties;
        this.clock = clock;
    }

    public synchronized void checkAddress(String address) {
        consume("ip:" + address, properties.getAddressAttempts(), properties.getAddressWindow());
    }

    public synchronized void checkAccount(String email) {
        consume(accountKey(email), properties.getAccountAttempts(), properties.getAccountWindow());
    }

    public synchronized void loginSucceeded(String email) {
        windows.remove(accountKey(email));
    }

    private String accountKey(String email) {
        return "account:" + email.trim().toLowerCase(Locale.ROOT);
    }

    private void consume(String key, int maximum, Duration duration) {
        Instant now = clock.instant();
        if (!now.isBefore(nextCleanup)) {
            windows.values().removeIf(window -> !now.isBefore(window.expiresAt));
            nextCleanup = now.plusSeconds(30);
        }
        Window window = windows.get(key);
        if (window != null && !now.isBefore(window.expiresAt)) {
            windows.remove(key);
            window = null;
        }
        if (window == null) {
            // No expulsar limites vigentes: inundar nuevas claves no debe resetear el bloqueo.
            if (windows.size() >= properties.getMaxTrackedKeys()) {
                throw new LoginRateLimitException(30);
            }
            window = new Window(now.plus(duration));
            windows.put(key, window);
        }
        if (window.attempts >= maximum) {
            long milliseconds = Duration.between(now, window.expiresAt).toMillis();
            throw new LoginRateLimitException(Math.max(1, (milliseconds + 999) / 1000));
        }
        window.attempts++;
    }

    private static final class Window {
        private final Instant expiresAt;
        private int attempts;

        private Window(Instant expiresAt) {
            this.expiresAt = expiresAt;
        }
    }
}
