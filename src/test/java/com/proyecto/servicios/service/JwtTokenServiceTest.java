package com.proyecto.servicios.service;

import com.proyecto.servicios.model.auth.AuthenticatedUser;
import com.proyecto.servicios.model.auth.CachedUser;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenServiceTest {

    @Test
    void generatesSignedTokenWithConfiguredExpiration() {
        JwtTokenService service = new JwtTokenService(
                "test-secret-with-more-than-32-bytes-123456789",
                "test-api",
                Duration.ofHours(2)
        );
        CachedUser user = new CachedUser(
                UUID.randomUUID(), "marti@example.com", "marti_01", "hash",
                "Martin Perez", true
        );
        UUID sessionId = UUID.randomUUID();

        Instant before = Instant.now().plus(Duration.ofHours(2)).minusSeconds(2);
        JwtTokenService.TokenResult result = service.generate(user, sessionId);
        AuthenticatedUser authenticated = service.validate(result.value());

        assertThat(result.value()).isNotBlank().contains(".");
        assertThat(result.expiresAt()).isAfter(before);
        assertThat(authenticated.userId()).isEqualTo(user.id());
        assertThat(authenticated.sessionId()).isEqualTo(sessionId);
        assertThat(authenticated.email()).isEqualTo(user.email());
    }

    @Test
    void rejectsTokenSignedWithAnotherKey() {
        JwtTokenService issuer = new JwtTokenService(
                "first-test-secret-with-more-than-32-bytes-123456",
                "test-api", Duration.ofHours(2)
        );
        JwtTokenService verifier = new JwtTokenService(
                "second-test-secret-with-more-than-32-bytes-12345",
                "test-api", Duration.ofHours(2)
        );
        CachedUser user = new CachedUser(
                UUID.randomUUID(), "marti@example.com", "marti_01", "hash",
                "Martin Perez", true
        );
        String token = issuer.generate(user, UUID.randomUUID()).value();

        assertThatThrownBy(() -> verifier.validate(token)).isInstanceOf(JwtException.class);
    }
}
