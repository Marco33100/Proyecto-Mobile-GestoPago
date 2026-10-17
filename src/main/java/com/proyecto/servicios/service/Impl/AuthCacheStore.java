package com.proyecto.servicios.service.Impl;

import com.proyecto.servicios.exception.auth.AuthCacheException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Component
public class AuthCacheStore {
    private static final DefaultRedisScript<Long> DELETE_MATCHING_SESSION = new DefaultRedisScript<>(
            "if redis.call('GET', KEYS[1]) == ARGV[1] then return redis.call('DEL', KEYS[1]) else return 0 end",
            Long.class);

    private final StringRedisTemplate redisTemplate;
    private final String userPrefix;
    private final String sessionPrefix;

    public AuthCacheStore(
            StringRedisTemplate redisTemplate,
            @Value("${auth.cache.user-prefix}") String userPrefix,
            @Value("${auth.cache.session-prefix}") String sessionPrefix
    ) {
        this.redisTemplate = redisTemplate;
        this.userPrefix = userPrefix;
        this.sessionPrefix = sessionPrefix;
    }

    public void invalidateUser(String email, UUID userId) {
        try {
            redisTemplate.delete(java.util.List.of(userPrefix + email, sessionPrefix + userId));
        } catch (Exception exception) {
            throw new AuthCacheException("No fue posible invalidar el acceso en Redis", exception);
        }
    }

    public void replaceSession(UUID userId, UUID sessionId, Instant expiresAt) {
        Duration ttl = Duration.between(Instant.now(), expiresAt);
        if (ttl.isNegative() || ttl.isZero()) {
            throw new AuthCacheException("La sesion ya se encuentra expirada", null);
        }
        try {
            redisTemplate.opsForValue().set(sessionPrefix + userId, sessionId.toString(), ttl);
        } catch (Exception exception) {
            throw new AuthCacheException("No fue posible guardar la sesion en Redis", exception);
        }
    }

    public void invalidateSessionIfMatches(UUID userId, UUID sessionId) {
        try {
            redisTemplate.execute(DELETE_MATCHING_SESSION, java.util.List.of(sessionPrefix + userId),
                    sessionId.toString());
        } catch (Exception exception) {
            throw new AuthCacheException("No fue posible invalidar la sesion en Redis", exception);
        }
    }

}
