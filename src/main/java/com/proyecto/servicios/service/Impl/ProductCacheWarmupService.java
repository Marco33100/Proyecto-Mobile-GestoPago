package com.proyecto.servicios.service.Impl;

import com.proyecto.servicios.exception.ProductCacheException;
import com.proyecto.servicios.model.product.ProductListResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/** Repuebla Redis en segundo plano cuando el catálogo se obtuvo desde PostgreSQL. */
@Slf4j
@Service
public class ProductCacheWarmupService {

    private final ProductCacheStore productCacheStore;

    @Autowired
    public ProductCacheWarmupService(ProductCacheStore productCacheStore) {
        this.productCacheStore = productCacheStore;
    }

    @Async("productCacheExecutor")
    public void warmup(ProductListResponse response) {
        try {
            productCacheStore.replaceCatalog(response);
            log.debug("Redis repoblado de forma asíncrona desde PostgreSQL");
        } catch (ProductCacheException exception) {
            log.warn("No se pudo repoblar Redis de forma asíncrona; PostgreSQL seguirá como respaldo");
        }
    }
}
