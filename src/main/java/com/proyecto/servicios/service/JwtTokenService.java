package com.proyecto.servicios.service;

import com.proyecto.servicios.model.auth.AuthenticatedUser;
import com.proyecto.servicios.model.auth.CachedUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Slf4j
@Service
public class JwtTokenService {

    private final SecretKey signingKey;
    private final String issuer;
    private final Duration expiration;

    @Autowired
    public JwtTokenService(
            @Value("${auth.jwt.secret:}") String configuredSecret,
            @Value("${auth.jwt.issuer:prueba-api}") String issuer,
            @Value("${auth.jwt.expiration:8h}") Duration expiration
    ) {
        this.signingKey = createSigningKey(configuredSecret);
        this.issuer = issuer;
        this.expiration = expiration;
    }

    public TokenResult generate(CachedUser user, UUID sessionId) {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(expiration);
        String token = Jwts.builder()
                .subject(user.id().toString())
                .id(sessionId.toString())
                .issuer(issuer)
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .claim("email", user.email())
                .claim("identifier", user.identifier())
                .signWith(signingKey)
                .compact();
        return new TokenResult(token, expiresAt);
    }

    public AuthenticatedUser validate(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .requireIssuer(issuer)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return new AuthenticatedUser(
                UUID.fromString(claims.getSubject()),
                UUID.fromString(claims.getId()),
                claims.get("email", String.class),
                claims.get("identifier", String.class)
        );
    }

    private SecretKey createSigningKey(String configuredSecret) {
        if (StringUtils.hasText(configuredSecret)) {
            byte[] secretBytes = configuredSecret.getBytes(StandardCharsets.UTF_8);
            if (secretBytes.length < 32) {
                throw new IllegalStateException("AUTH_JWT_SECRET debe contener al menos 32 bytes");
            }
            return Keys.hmacShaKeyFor(secretBytes);
        }

        byte[] generatedSecret = new byte[32];
        new SecureRandom().nextBytes(generatedSecret);
        log.warn("AUTH_JWT_SECRET no esta configurado; se usara una llave temporal hasta reiniciar la aplicacion");
        return Keys.hmacShaKeyFor(generatedSecret);
    }

    public record TokenResult(String value, Instant expiresAt) {
    }
}
