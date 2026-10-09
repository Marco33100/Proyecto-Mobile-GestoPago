package com.proyecto.servicios.service.Impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.proyecto.servicios.entity.sf.UserEntity;
import com.proyecto.servicios.exception.auth.AuthCacheException;
import com.proyecto.servicios.model.auth.CachedUser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Component
public class AuthCacheStore {
    private static final DefaultRedisScript<Long> DELETE_MATCHING_SESSION = new DefaultRedisScript<>(
            "if redis.call('GET', KEYS[1]) == ARGV[1] then return redis.call('DEL', KEYS[1]) else return 0 end",
            Long.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final String userPrefix;
    private final String sessionPrefix;
    private final Duration userTtl;

    public AuthCacheStore(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            @Value("${auth.cache.user-prefix}") String userPrefix,
            @Value("${auth.cache.session-prefix}") String sessionPrefix,
            @Value("${auth.cache.user-ttl}") Duration userTtl
    ) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.userPrefix = userPrefix;
        this.sessionPrefix = sessionPrefix;
        this.userTtl = userTtl;
    }

    public void cacheUser(UserEntity user) {
        CachedUser cachedUser = new CachedUser(
                user.getId(), user.getEmail(), user.getIdentifier(), user.getPasswordHash(),
                user.getFullName(), user.isEnabled()
        );
        try {
            redisTemplate.opsForValue().set(
                    userPrefix + user.getEmail(),
                    objectMapper.writeValueAsString(cachedUser),
                    userTtl
            );
        } catch (Exception exception) {
            throw new AuthCacheException("No fue posible guardar el usuario en Redis", exception);
        }
    }

    public void invalidateUser(String email, UUID userId) {
        try {
            redisTemplate.delete(java.util.List.of(userPrefix + email, sessionPrefix + userId));
        } catch (Exception exception) {
            throw new AuthCacheException("No fue posible invalidar el acceso en Redis", exception);
        }
    }

    public Optional<CachedUser> findUser(String normalizedEmail) {
        try {
            String json = redisTemplate.opsForValue().get(userPrefix + normalizedEmail);
            if (!StringUtils.hasText(json)) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(json, CachedUser.class));
        } catch (Exception exception) {
            throw new AuthCacheException("No fue posible consultar el usuario en Redis", exception);
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

    public Optional<UUID> findSessionId(UUID userId) {
        try {
            String value = redisTemplate.opsForValue().get(sessionPrefix + userId);
            return StringUtils.hasText(value) ? Optional.of(UUID.fromString(value)) : Optional.empty();
        } catch (Exception exception) {
            throw new AuthCacheException("No fue posible consultar la sesion en Redis", exception);
        }
    }
}
