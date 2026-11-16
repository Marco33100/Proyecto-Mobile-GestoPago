package com.proyecto.servicios.service;

import com.proyecto.servicios.model.auth.AuthenticatedUser;
import com.proyecto.servicios.model.auth.CachedUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
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
    private static final String CLAIM_EMAIL = "email";
    private static final String CLAIM_IDENTIFIER = "identifier";
    private static final String CLAIM_PURPOSE = "purpose";
    private static final String PURPOSE_LOGIN_2FA = "login-2fa";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final SecretKey signingKey;
    private final JwtParser parser;
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
        // El parser construido es inmutable y seguro para reutilizar entre peticiones.
        this.parser = Jwts.parser().verifyWith(signingKey).requireIssuer(issuer).build();
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
                .claim(CLAIM_EMAIL, user.email())
                .claim(CLAIM_IDENTIFIER, user.identifier())
                .signWith(signingKey)
                .compact();
        return new TokenResult(token, expiresAt);
    }

    public AuthenticatedUser validate(String token) {
        Claims claims = parser.parseSignedClaims(token).getPayload();
        if (claims.get(CLAIM_PURPOSE) != null // un desafio de login nunca es una sesion
                || claims.getExpiration() == null || claims.getIssuedAt() == null
                || !claims.getExpiration().after(claims.getIssuedAt())
                || !StringUtils.hasText(claims.get(CLAIM_EMAIL, String.class))
                || !StringUtils.hasText(claims.get(CLAIM_IDENTIFIER, String.class))) {
            throw new MalformedJwtException("El JWT no contiene los datos de sesion requeridos");
        }
        return new AuthenticatedUser(
                UUID.fromString(claims.getSubject()),
                UUID.fromString(claims.getId()),
                claims.get(CLAIM_EMAIL, String.class),
                claims.get(CLAIM_IDENTIFIER, String.class)
        );
    }

    /**
     * Desafio de inicio de sesion: prueba que la contrasena ya se verifico. No sirve como
     * sesion (no lleva email/identifier y {@link #validate} lo rechaza) y vence en minutos.
     */
    public TokenResult generateLoginChallenge(UUID userId, UUID challengeId, Duration validity) {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(validity);
        String token = Jwts.builder()
                .subject(userId.toString())
                .id(challengeId.toString())
                .issuer(issuer)
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .claim(CLAIM_PURPOSE, PURPOSE_LOGIN_2FA)
                .signWith(signingKey)
                .compact();
        return new TokenResult(token, expiresAt);
    }

    /** Devuelve usuario y desafio; lanza JwtException si el token no es un desafio vigente. */
    public LoginChallenge validateLoginChallenge(String token) {
        Claims claims = parser.parseSignedClaims(token).getPayload();
        if (!PURPOSE_LOGIN_2FA.equals(claims.get(CLAIM_PURPOSE, String.class))
                || claims.getExpiration() == null || claims.getId() == null) {
            throw new MalformedJwtException("El token no es un desafio de inicio de sesion");
        }
        return new LoginChallenge(UUID.fromString(claims.getSubject()), UUID.fromString(claims.getId()));
    }

    public record LoginChallenge(UUID userId, UUID challengeId) {
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
        SECURE_RANDOM.nextBytes(generatedSecret);
        log.warn("AUTH_JWT_SECRET no esta configurado; se usara una llave temporal hasta reiniciar la aplicacion");
        return Keys.hmacShaKeyFor(generatedSecret);
    }

    public record TokenResult(String value, Instant expiresAt) {
    }
}
