package com.proyecto.servicios.service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.proyecto.servicios.exception.ProductCacheException;
import com.proyecto.servicios.exception.ProductIntegrationErrorType;
import com.proyecto.servicios.exception.ProductIntegrationException;
import com.proyecto.servicios.model.product.ProductContainer;
import com.proyecto.servicios.model.product.ProductDto;
import com.proyecto.servicios.model.product.ProductListResponse;
import com.proyecto.servicios.service.Impl.GestopagoProductGateway;
import com.proyecto.servicios.service.Impl.ProductCacheStore;
import com.proyecto.servicios.service.Impl.ProductCacheWarmupService;
import com.proyecto.servicios.service.Impl.ProductCatalogRefreshLock;
import com.proyecto.servicios.service.Impl.ProductCatalogServiceImpl;
import com.proyecto.servicios.service.Impl.ProductCatalogSyncScheduler;
import com.proyecto.servicios.service.Impl.ProductDatabaseService;
import com.proyecto.servicios.service.Impl.ProductSnapshotFileService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductCatalogLogTest {
    private static final String SECRET = "fixture-password-and-token-in-cause";
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private final List<Logger> loggers = List.of(
            (Logger) LoggerFactory.getLogger(ProductCatalogServiceImpl.class),
            (Logger) LoggerFactory.getLogger(ProductCatalogSyncScheduler.class));

    @Mock private GestopagoProductGateway gateway;
    @Mock private ProductDatabaseService database;
    @Mock private ObjectProvider<ProductDatabaseService> databaseProvider;
    @Mock private ProductCacheStore cache;
    @Mock private ProductSnapshotFileService snapshots;
    @Mock private ProductCacheWarmupService warmup;
    @Mock private GestopagoProductService providerService;

    @BeforeEach
    void registrarLogs() {
        appender.start();
        loggers.forEach(logger -> logger.addAppender(appender));
    }

    @AfterEach
    void limpiarLogs() {
        loggers.forEach(logger -> logger.detachAppender(appender));
        appender.stop();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void fallosDeSincronizacionNoImprimenElMensajeNiLaCausa(boolean controlled) {
        RuntimeException error = controlled
                ? new ProductIntegrationException(ProductIntegrationErrorType.GESTOPAGO_UNAVAILABLE,
                        "password=" + SECRET, new IllegalStateException("Bearer " + SECRET))
                : new IllegalStateException("jdbc:postgresql://provider?password=" + SECRET);
        when(gateway.fetchCatalog()).thenThrow(error);
        var scheduler = new ProductCatalogSyncScheduler(gateway, database, cache, snapshots,
                new ProductCatalogRefreshLock());

        scheduler.synchronizeDailyCatalog();

        verificarLogsSeguros();
        verifyNoInteractions(database, cache);
    }

    @Test
    void falloRedisConservaElFallbackSinRegistrarDetallesSensibles() {
        when(cache.findCatalog()).thenThrow(new ProductCacheException("password=" + SECRET,
                new IllegalStateException("Bearer " + SECRET)));
        when(databaseProvider.getIfAvailable()).thenReturn(database);
        var products = new ProductContainer();
        products.setProductos(List.of(new ProductDto()));
        var expected = new ProductListResponse();
        expected.setProductos(products);
        when(database.findCatalog()).thenReturn(expected);
        var service = new ProductCatalogServiceImpl(cache, databaseProvider, providerService,
                new ProductCatalogRefreshLock(), warmup);

        assertThat(service.obtenerProductos()).isSameAs(expected);

        verificarLogsSeguros();
        verifyNoInteractions(providerService, warmup);
    }

    private void verificarLogsSeguros() {
        assertThat(appender.list).isNotEmpty();
        appender.list.forEach(event -> {
            assertThat(event.getFormattedMessage()).doesNotContain(SECRET, "password=", "Bearer ", "jdbc:");
            assertThat(event.getThrowableProxy()).isNull();
        });
    }
}
