package com.proyecto.servicios.service;

import com.proyecto.servicios.config.LoginRateLimitProperties;
import com.proyecto.servicios.exception.auth.LoginRateLimitException;
import com.proyecto.servicios.service.Impl.LoginAttemptLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class LoginAttemptLimiterTest {
    private LoginRateLimitProperties properties;
    private Clock clock;
    private LoginAttemptLimiter limiter;

    @BeforeEach
    void setUp() {
        properties = new LoginRateLimitProperties();
        properties.setAccountAttempts(2);
        properties.setAddressAttempts(2);
        clock = mock(Clock.class);
        when(clock.instant()).thenReturn(Instant.parse("2026-10-09T00:00:00Z"));
        limiter = new LoginAttemptLimiter(properties, clock);
    }

    @Test
    void bloqueaCuentaNormalizadaYLiberaTrasLaVentana() {
        limiter.checkAccount(" Uno@example.com ");
        limiter.checkAccount("uno@example.com");
        assertThatThrownBy(() -> limiter.checkAccount("UNO@example.com"))
                .isInstanceOf(LoginRateLimitException.class)
                .satisfies(e -> assertThat(((LoginRateLimitException) e).getRetryAfterSeconds()).isEqualTo(300));
        when(clock.instant()).thenReturn(Instant.parse("2026-10-09T00:05:00Z"));
        assertThatCode(() -> limiter.checkAccount("uno@example.com")).doesNotThrowAnyException();
    }

    @Test
    void loginCorrectoReiniciaCuentaPeroNoLimiteDeIp() {
        limiter.checkAccount("uno@example.com");
        limiter.checkAccount("uno@example.com");
        limiter.checkAddress("127.0.0.1");
        limiter.checkAddress("127.0.0.1");
        limiter.loginSucceeded("UNO@example.com");
        assertThatCode(() -> limiter.checkAccount("uno@example.com")).doesNotThrowAnyException();
        assertThatThrownBy(() -> limiter.checkAddress("127.0.0.1")).isInstanceOf(LoginRateLimitException.class);
    }

    @Test
    void memoriaAcotadaNoExpulsaBloqueosVigentes() {
        properties.setMaxTrackedKeys(1);
        limiter.checkAccount("uno@example.com");
        limiter.checkAccount("uno@example.com");
        assertThatThrownBy(() -> limiter.checkAccount("otro@example.com")).isInstanceOf(LoginRateLimitException.class);
        assertThatThrownBy(() -> limiter.checkAccount("uno@example.com")).isInstanceOf(LoginRateLimitException.class);
    }

    @Test
    void concurrenciaNoSuperaElLimiteDeCuenta() throws Exception {
        var executor = Executors.newFixedThreadPool(8);
        try {
            var results = new ArrayList<Future<Boolean>>();
            for (int i = 0; i < 20; i++) {
                results.add(executor.submit(() -> {
                    try { limiter.checkAccount("uno@example.com"); return true; }
                    catch (LoginRateLimitException exception) { return false; }
                }));
            }
            int allowed = 0;
            for (var result : results) if (result.get()) allowed++;
            assertThat(allowed).isEqualTo(2);
        } finally {
            executor.shutdownNow();
        }
    }
}
