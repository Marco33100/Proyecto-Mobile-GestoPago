package com.proyecto.servicios.service.Impl;

import com.proyecto.servicios.exception.ProductCacheException;
import com.proyecto.servicios.exception.ProductIntegrationErrorType;
import com.proyecto.servicios.exception.ProductIntegrationException;
import com.proyecto.servicios.model.product.ProductListResponse;
import com.proyecto.servicios.model.product.ProductSyncResult;
import com.proyecto.servicios.service.ProductCatalogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Slf4j
@Service
public class ProductCatalogServiceImpl implements ProductCatalogService {

    private final ProductCacheStore productCacheStore;
    private final ObjectProvider<ProductDatabaseService> databaseServiceProvider;
    private final GestopagoProductGateway gestopagoProductGateway;
    private final ProductCatalogRefreshLock refreshLock;

    public ProductCatalogServiceImpl(
            ProductCacheStore productCacheStore,
            ObjectProvider<ProductDatabaseService> databaseServiceProvider,
            GestopagoProductGateway gestopagoProductGateway,
            ProductCatalogRefreshLock refreshLock
    ) {
        this.productCacheStore = productCacheStore;
        this.databaseServiceProvider = databaseServiceProvider;
        this.gestopagoProductGateway = gestopagoProductGateway;
        this.refreshLock = refreshLock;
    }

    @Override
    public ProductListResponse obtenerProductos() {
        CacheLookup cacheLookup = findInRedis();
        if (cacheLookup.value().isPresent()) {
            log.debug("Catálogo de productos recuperado desde Redis");
            return cacheLookup.value().get();
        }

        ProductDatabaseService databaseService = databaseServiceProvider.getIfAvailable();
        if (cacheLookup.available()) {
            return refreshLock.execute(() -> refreshAfterCacheMiss(databaseService));
        }

        // Redis caído: PostgreSQL puede atender concurrentemente sin bloquear todas las peticiones.
        Optional<ProductListResponse> stored = findInPostgres(databaseService);
        if (stored.isPresent()) {
            log.debug("Catálogo de productos recuperado desde PostgreSQL");
            return stored.get();
        }

        return refreshLock.execute(() -> refreshAfterCacheMiss(databaseService));
    }

    private ProductListResponse refreshAfterCacheMiss(ProductDatabaseService databaseService) {
        CacheLookup cacheLookup = findInRedis();
        if (cacheLookup.value().isPresent()) {
            return cacheLookup.value().get();
        }

        Optional<ProductListResponse> stored = findInPostgres(databaseService);
        if (stored.isPresent()) {
            if (cacheLookup.available()) {
                updateRedisSafely(stored.get());
            }
            return stored.get();
        }

        ProductListResponse externalResponse = gestopagoProductGateway.fetchCatalog();
        ProductListResponse selectedResponse = persistExternalResponse(databaseService, externalResponse);
        updateRedisSafely(selectedResponse);
        return selectedResponse;
    }

    private Optional<ProductListResponse> findInPostgres(ProductDatabaseService databaseService) {
        if (databaseService != null) {
            try {
                return databaseService.findCatalog();
            } catch (ProductIntegrationException exception) {
                log.error("PostgreSQL no estuvo disponible; se intentará Gestopago: codigo={}",
                        exception.getErrorType().getCode());
            }
        }
        return Optional.empty();
    }

    private CacheLookup findInRedis() {
        try {
            return new CacheLookup(productCacheStore.findCatalog(), true);
        } catch (ProductCacheException exception) {
            log.warn("Redis no estuvo disponible; se utilizará el siguiente fallback");
            return new CacheLookup(Optional.empty(), false);
        }
    }

    private ProductListResponse persistExternalResponse(
            ProductDatabaseService databaseService,
            ProductListResponse response
    ) {
        if (databaseService == null) {
            throw new ProductIntegrationException(
                    ProductIntegrationErrorType.DATABASE_ERROR,
                    "Gestopago respondió, pero PostgreSQL no está habilitado"
            );
        }

        try {
            ProductSyncResult result = databaseService.replaceIfLarger(response);
            if (!result.updated()) {
                log.warn(
                        "No se reemplazó PostgreSQL: recibidos={}, existentes={}",
                        result.receivedCount(),
                        result.previousCount()
                );
                return databaseService.findCatalog().orElseThrow(() ->
                        new ProductIntegrationException(
                                ProductIntegrationErrorType.DATABASE_ERROR,
                                "No fue posible recuperar el catálogo vigente de PostgreSQL"
                        )
                );
            }
            return response;
        } catch (ProductIntegrationException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ProductIntegrationException(
                    ProductIntegrationErrorType.DATABASE_ERROR,
                    "Gestopago respondió, pero el catálogo no pudo guardarse en PostgreSQL",
                    exception
            );
        }
    }

    private void updateRedisSafely(ProductListResponse response) {
        try {
            productCacheStore.replaceCatalog(response);
        } catch (ProductCacheException exception) {
            log.warn("No se pudo actualizar Redis; PostgreSQL continuará como fallback");
        }
    }

    private record CacheLookup(
            Optional<ProductListResponse> value,
            boolean available
    ) {
    }
}
