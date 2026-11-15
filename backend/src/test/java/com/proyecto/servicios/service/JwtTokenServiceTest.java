package com.proyecto.servicios.service;

import com.proyecto.servicios.model.auth.AuthenticatedUser;
import com.proyecto.servicios.model.auth.CachedUser;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.Date;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenServiceTest {

    @Test
    void parserCompartidoValidaSesionesConcurrentesSinMezclarIdentidades() throws Exception {
        JwtTokenService service = new JwtTokenService(
                "test-secret-with-more-than-32-bytes-123456789", "test-api", Duration.ofHours(2));
        var users = IntStream.range(0, 32)
                .mapToObj(index -> new CachedUser(UUID.randomUUID(), "user" + index + "@example.com",
                        "user" + index, "fixture-hash", "Usuario de prueba", true)).toList();
        var sessionIds = users.stream().map(user -> UUID.randomUUID()).toList();
        var tokens = IntStream.range(0, users.size())
                .mapToObj(index -> service.generate(users.get(index), sessionIds.get(index)).value()).toList();
        var tasks = IntStream.range(0, users.size()).mapToObj(index -> (Callable<Void>) () -> {
            for (int repetition = 0; repetition < 4; repetition++) {
                AuthenticatedUser result = service.validate(tokens.get(index));
                assertThat(result.userId()).isEqualTo(users.get(index).id());
                assertThat(result.sessionId()).isEqualTo(sessionIds.get(index));
                assertThat(result.email()).isEqualTo(users.get(index).email());
                assertThat(result.identifier()).isEqualTo(users.get(index).identifier());
            }
            return null;
        }).toList();
        var executor = Executors.newFixedThreadPool(8);
        try {
            for (var result : executor.invokeAll(tasks, 15, TimeUnit.SECONDS)) {
                result.get(1, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void rechazaJwtExpiradoYSinExpiracionAunqueLaFirmaSeaCorrecta() {
        String secret = "test-secret-with-more-than-32-bytes-123456789";
        JwtTokenService service = new JwtTokenService(secret, "test-api", Duration.ofHours(2));
        var key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        var builder = Jwts.builder().subject(UUID.randomUUID().toString()).id(UUID.randomUUID().toString())
                .issuer("test-api").issuedAt(Date.from(Instant.now().minusSeconds(30)))
                .claim("email", "uno@example.com").claim("identifier", "uno@example.com");
        String sinExpiracion = builder.signWith(key).compact();
        assertThatThrownBy(() -> service.validate(sinExpiracion)).isInstanceOf(JwtException.class);
        String expirado = builder.expiration(Date.from(Instant.now().minusSeconds(1))).signWith(key).compact();
        assertThatThrownBy(() -> service.validate(expirado)).isInstanceOf(JwtException.class);
    }

    @Test
    void rechazaEmisorDistinto() {
        String secret = "test-secret-with-more-than-32-bytes-123456789";
        var issuer = new JwtTokenService(secret, "otro-emisor", Duration.ofHours(2));
        var verifier = new JwtTokenService(secret, "test-api", Duration.ofHours(2));
        String token = issuer.generate(new CachedUser(UUID.randomUUID(), "uno@example.com", "uno", "hash",
                "Marco Martinez", true), UUID.randomUUID()).value();
        assertThatThrownBy(() -> verifier.validate(token)).isInstanceOf(JwtException.class);
    }

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
