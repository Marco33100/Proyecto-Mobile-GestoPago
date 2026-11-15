package com.proyecto.servicios.service.Impl;

import com.proyecto.servicios.exception.ProductCacheException;
import com.proyecto.servicios.exception.ProductIntegrationErrorType;
import com.proyecto.servicios.exception.ProductIntegrationException;
import com.proyecto.servicios.model.product.ProductListResponse;
import com.proyecto.servicios.model.product.ProductSyncResult;
import com.proyecto.servicios.service.GestopagoProductService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/** Ejecuta el fallback interno: Gestopago -> XML/DTO -> PostgreSQL -> Redis. */
@Slf4j
@Service
public class GestopagoProductServiceImpl implements GestopagoProductService {

    private final GestopagoProductGateway gestopagoProductGateway;
    private final ObjectProvider<ProductDatabaseService> databaseServiceProvider;
    private final ProductCacheStore productCacheStore;

    @Autowired
    public GestopagoProductServiceImpl(
            GestopagoProductGateway gestopagoProductGateway,
            ObjectProvider<ProductDatabaseService> databaseServiceProvider,
            ProductCacheStore productCacheStore
    ) {
        this.gestopagoProductGateway = gestopagoProductGateway;
        this.databaseServiceProvider = databaseServiceProvider;
        this.productCacheStore = productCacheStore;
    }

    @Override
    public ProductListResponse obtenerYGuardarProductos() {
        ProductListResponse externalResponse = gestopagoProductGateway.fetchCatalog();
        ProductDatabaseService databaseService = databaseServiceProvider.getIfAvailable();
        if (databaseService == null) {
            throw new ProductIntegrationException(
                    ProductIntegrationErrorType.DATABASE_ERROR,
                    "Gestopago respondió, pero PostgreSQL no está habilitado"
            );
        }

        ProductListResponse selectedResponse = persistCatalog(databaseService, externalResponse);
        updateRedisSafely(selectedResponse);
        return selectedResponse;
    }

    private ProductListResponse persistCatalog(
            ProductDatabaseService databaseService,
            ProductListResponse response
    ) {
        try {
            ProductSyncResult result = databaseService.replaceIfLarger(response);
            if (result.updated()) {
                return response;
            }

            log.warn(
                    "No se reemplazó PostgreSQL: recibidos={}, existentes={}",
                    result.receivedCount(),
                    result.previousCount()
            );
            ProductListResponse stored = databaseService.findCatalog();
            if (!ProductCatalogResponses.hasProducts(stored)) {
                throw new ProductIntegrationException(
                        ProductIntegrationErrorType.DATABASE_ERROR,
                        "No fue posible recuperar el catálogo vigente de PostgreSQL"
                );
            }
            return stored;
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
            log.warn("No se pudo actualizar Redis; PostgreSQL conservará el catálogo");
        }
    }

}
