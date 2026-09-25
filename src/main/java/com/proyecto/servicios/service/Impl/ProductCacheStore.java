package com.proyecto.servicios.service.Impl;

import com.proyecto.servicios.exception.ProductCacheException;
import com.proyecto.servicios.model.product.ProductListResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class ProductCacheStore {

    private final RedisTemplate<String, ProductListResponse> redisTemplate;
    private final String cacheKey;
    private final Duration cacheTtl;

    @Autowired
    public ProductCacheStore(
            @Qualifier("productRedisTemplate") RedisTemplate<String, ProductListResponse> redisTemplate,
            @Value("${product.cache.key}") String cacheKey,
            @Value("${product.cache.ttl}") Duration cacheTtl
    ) {
        this.redisTemplate = redisTemplate;
        this.cacheKey = cacheKey;
        this.cacheTtl = cacheTtl;
    }

    public ProductListResponse findCatalog() {
        try {
            ProductListResponse response = redisTemplate.opsForValue().get(cacheKey);
            return response == null ? new ProductListResponse() : response;
        } catch (Exception exception) {
            throw new ProductCacheException("No fue posible consultar el catálogo en Redis", exception);
        }
    }

    public void replaceCatalog(ProductListResponse response) {
        try {
            redisTemplate.opsForValue().set(cacheKey, response, cacheTtl);
        } catch (Exception exception) {
            throw new ProductCacheException("No fue posible actualizar el catálogo en Redis", exception);
        }
    }
}
