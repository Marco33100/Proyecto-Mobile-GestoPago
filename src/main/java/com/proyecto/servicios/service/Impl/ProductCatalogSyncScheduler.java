package com.proyecto.servicios.service.Impl;

import com.proyecto.servicios.exception.ProductCacheException;
import com.proyecto.servicios.exception.ProductIntegrationException;
import com.proyecto.servicios.model.product.ProductListResponse;
import com.proyecto.servicios.model.product.ProductSyncResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

@Slf4j
@Component
@ConditionalOnProperty(name = "app.database.enabled", havingValue = "true")
public class ProductCatalogSyncScheduler {

    private final GestopagoProductGateway gestopagoProductGateway;
    private final ProductDatabaseService productDatabaseService;
    private final ProductCacheStore productCacheStore;
    private final ProductSnapshotFileService snapshotFileService;
    private final ProductCatalogRefreshLock refreshLock;

    @Autowired
    public ProductCatalogSyncScheduler(
            GestopagoProductGateway gestopagoProductGateway,
            ProductDatabaseService productDatabaseService,
            ProductCacheStore productCacheStore,
            ProductSnapshotFileService snapshotFileService,
            ProductCatalogRefreshLock refreshLock
    ) {
        this.gestopagoProductGateway = gestopagoProductGateway;
        this.productDatabaseService = productDatabaseService;
        this.productCacheStore = productCacheStore;
        this.snapshotFileService = snapshotFileService;
        this.refreshLock = refreshLock;
    }

    private void performSynchronization() {
        Path snapshot = null;
        log.info("Inicia sincronización diaria del catálogo Gestopago");
        try {
            ProductListResponse response = gestopagoProductGateway.fetchCatalog();
            snapshot = snapshotFileService.writeTemporarySnapshot(response);

            ProductSyncResult result = productDatabaseService.replaceIfLarger(response);
            if (!result.updated()) {
                log.warn(
                        "Sincronizacion omitida: catalogo vacio o cantidad no mayor; nuevos={}, existentes={}",
                        result.receivedCount(),
                        result.previousCount()
                );
                return;
            }

            try {
                productCacheStore.replaceCatalog(response);
                log.info("Redis sobrescrito después del commit de PostgreSQL");
            } catch (ProductCacheException exception) {
                log.error("PostgreSQL se actualizó, pero Redis no pudo sobrescribirse");
            }

            log.info(
                    "Sincronización diaria completada; anteriores={}, guardados={}",
                    result.previousCount(),
                    result.receivedCount()
            );
        } catch (ProductIntegrationException exception) {
            log.error(
                    "Falló la sincronización diaria del catálogo: codigo={}, mensaje={}",
                    exception.getErrorType().getCode(),
                    exception.getMessage()
            );
        } catch (Exception exception) {
            log.error("Falló inesperadamente la sincronización diaria del catálogo", exception);
        } finally {
            snapshotFileService.deleteSnapshot(snapshot);
            log.info("Finaliza sincronización diaria del catálogo Gestopago");
        }
    }

    @Async("productSyncExecutor")
    @Scheduled(cron = "${product.sync.cron}", zone = "${product.sync.zone}")
    public void synchronizeDailyCatalog() {
        if (!refreshLock.tryExecute(this::performSynchronization)) {
            log.warn("Se omitió la sincronización porque existe otra actualización en curso");
        }
    }
}
