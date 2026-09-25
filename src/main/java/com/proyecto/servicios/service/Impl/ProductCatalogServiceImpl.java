package com.proyecto.servicios.service.Impl;

import com.proyecto.servicios.exception.ProductCacheException;
import com.proyecto.servicios.exception.ProductIntegrationException;
import com.proyecto.servicios.model.product.ProductListResponse;
import com.proyecto.servicios.service.GestopagoProductService;
import com.proyecto.servicios.service.ProductCatalogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class ProductCatalogServiceImpl implements ProductCatalogService {

    private final ProductCacheStore productCacheStore;
    private final ObjectProvider<ProductDatabaseService> databaseServiceProvider;
    private final GestopagoProductService gestopagoProductService;
    private final ProductCatalogRefreshLock refreshLock;
    private final ProductCacheWarmupService productCacheWarmupService;

    @Autowired
    public ProductCatalogServiceImpl(
            ProductCacheStore productCacheStore,
            ObjectProvider<ProductDatabaseService> databaseServiceProvider,
            GestopagoProductService gestopagoProductService,
            ProductCatalogRefreshLock refreshLock,
            ProductCacheWarmupService productCacheWarmupService
    ) {
        this.productCacheStore = productCacheStore;
        this.databaseServiceProvider = databaseServiceProvider;
        this.gestopagoProductService = gestopagoProductService;
        this.refreshLock = refreshLock;
        this.productCacheWarmupService = productCacheWarmupService;
    }

    @Override
    public ProductListResponse obtenerProductos() {
        CacheLookup cacheLookup = findInRedis();
        if (ProductCatalogResponses.hasProducts(cacheLookup.value())) {
            log.debug("Catálogo de productos recuperado desde Redis");
            return cacheLookup.value();
        }

        ProductDatabaseService databaseService = databaseServiceProvider.getIfAvailable();
        if (cacheLookup.available()) {
            return refreshLock.execute(() -> refreshAfterCacheMiss(databaseService));
        }

        // Redis caído: PostgreSQL puede atender concurrentemente sin bloquear todas las peticiones.
        ProductListResponse stored = findInPostgres(databaseService);
        if (ProductCatalogResponses.hasProducts(stored)) {
            log.debug("Catálogo de productos recuperado desde PostgreSQL");
            return stored;
        }

        return refreshLock.execute(() -> refreshAfterCacheMiss(databaseService));
    }

    private ProductListResponse refreshAfterCacheMiss(ProductDatabaseService databaseService) {
        CacheLookup cacheLookup = findInRedis();
        if (ProductCatalogResponses.hasProducts(cacheLookup.value())) {
            return cacheLookup.value();
        }

        ProductListResponse stored = findInPostgres(databaseService);
        if (ProductCatalogResponses.hasProducts(stored)) {
            if (cacheLookup.available()) {
                productCacheWarmupService.warmup(stored);
            }
            return stored;
        }

        return gestopagoProductService.obtenerYGuardarProductos();
    }

    private ProductListResponse findInPostgres(ProductDatabaseService databaseService) {
        if (databaseService != null) {
            try {
                return databaseService.findCatalog();
            } catch (ProductIntegrationException exception) {
                log.error("PostgreSQL no estuvo disponible; se intentará Gestopago: codigo={}",
                        exception.getErrorType().getCode());
            }
        }
        return new ProductListResponse();
    }

    private CacheLookup findInRedis() {
        try {
            return new CacheLookup(productCacheStore.findCatalog(), true);
        } catch (ProductCacheException exception) {
            log.error("Redis no estuvo disponible; se utilizará el siguiente fallback", exception);
            return new CacheLookup(new ProductListResponse(), false);
        }
    }

    private record CacheLookup(
            ProductListResponse value,
            boolean available
    ) {
    }
}
