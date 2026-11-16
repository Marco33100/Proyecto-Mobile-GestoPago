package com.proyecto.servicios.service.Impl;

import com.proyecto.servicios.exception.auth.AuthCacheException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class AuthCacheStore {
    private final StringRedisTemplate redisTemplate;
    private final String userPrefix;

    public AuthCacheStore(StringRedisTemplate redisTemplate, @Value("${auth.cache.user-prefix}") String userPrefix) {
        this.redisTemplate = redisTemplate;
        this.userPrefix = userPrefix;
    }

    public void invalidateUser(String email) {
        try {
            redisTemplate.delete(userPrefix + email);
        } catch (Exception exception) {
            throw new AuthCacheException("No fue posible invalidar el acceso en Redis", exception);
        }
    }
}
