package com.proyecto.servicios.service.Impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.proyecto.servicios.exception.ProductCacheException;
import com.proyecto.servicios.model.product.ProductListResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Optional;

@Component
public class ProductCacheStore {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final String cacheKey;
    private final Duration cacheTtl;

    public ProductCacheStore(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            @Value("${product.cache.key}") String cacheKey,
            @Value("${product.cache.ttl}") Duration cacheTtl
    ) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.cacheKey = cacheKey;
        this.cacheTtl = cacheTtl;
    }

    public Optional<ProductListResponse> findCatalog() {
        try {
            String json = redisTemplate.opsForValue().get(cacheKey);
            if (!StringUtils.hasText(json)) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(json, ProductListResponse.class));
        } catch (Exception exception) {
            throw new ProductCacheException("No fue posible consultar el catálogo en Redis", exception);
        }
    }

    public void replaceCatalog(ProductListResponse response) {
        try {
            String json = objectMapper.writeValueAsString(response);
            redisTemplate.opsForValue().set(cacheKey, json, cacheTtl);
        } catch (JsonProcessingException exception) {
            throw new ProductCacheException("No fue posible serializar el catálogo para Redis", exception);
        } catch (Exception exception) {
            throw new ProductCacheException("No fue posible actualizar el catálogo en Redis", exception);
        }
    }
}
