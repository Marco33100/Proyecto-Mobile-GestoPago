package com.proyecto.servicios.service;

import com.proyecto.servicios.exception.ProductCacheException;
import com.proyecto.servicios.exception.ProductIntegrationErrorType;
import com.proyecto.servicios.exception.ProductIntegrationException;
import com.proyecto.servicios.model.product.ProductContainer;
import com.proyecto.servicios.model.product.ProductListResponse;
import com.proyecto.servicios.model.product.ProductMessage;
import com.proyecto.servicios.service.Impl.ProductCacheStore;
import com.proyecto.servicios.service.Impl.ProductCacheWarmupService;
import com.proyecto.servicios.service.Impl.ProductCatalogServiceImpl;
import com.proyecto.servicios.service.Impl.ProductCatalogRefreshLock;
import com.proyecto.servicios.service.Impl.ProductDatabaseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductCatalogServiceImplTest {

    @Mock
    private ProductCacheStore cacheStore;
    @Mock
    private ObjectProvider<ProductDatabaseService> databaseServiceProvider;
    @Mock
    private ProductDatabaseService databaseService;
    @Mock
    private GestopagoProductService gestopagoProductService;
    @Mock
    private ProductCacheWarmupService cacheWarmupService;

    private ProductCatalogServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ProductCatalogServiceImpl(
                cacheStore,
                databaseServiceProvider,
                gestopagoProductService,
                new ProductCatalogRefreshLock(),
                cacheWarmupService
        );
    }

    @Test
    void returnsRedisCatalogWithoutCallingOtherSources() {
        ProductListResponse cached = response();
        when(cacheStore.findCatalog()).thenReturn(cached);

        ProductListResponse result = service.obtenerProductos();

        assertSame(cached, result);
        verify(databaseServiceProvider, never()).getIfAvailable();
        verify(gestopagoProductService, never()).obtenerYGuardarProductos();
    }

    @Test
    void fallsBackToPostgresAndWarmsRedis() {
        ProductListResponse stored = response();
        when(cacheStore.findCatalog()).thenReturn(new ProductListResponse());
        when(databaseServiceProvider.getIfAvailable()).thenReturn(databaseService);
        when(databaseService.findCatalog()).thenReturn(stored);

        ProductListResponse result = service.obtenerProductos();

        assertSame(stored, result);
        verify(cacheWarmupService).warmup(stored);
        verify(cacheStore, never()).replaceCatalog(stored);
        verify(gestopagoProductService, never()).obtenerYGuardarProductos();
    }

    @Test
    void redisFailureStillFallsBackToPostgres() {
        ProductListResponse stored = response();
        when(cacheStore.findCatalog()).thenThrow(new ProductCacheException("redis", new RuntimeException()));
        when(databaseServiceProvider.getIfAvailable()).thenReturn(databaseService);
        when(databaseService.findCatalog()).thenReturn(stored);

        assertSame(stored, service.obtenerProductos());
    }

    @Test
    void emptyRedisAndDatabaseFetchGestopagoThenPopulateBothStores() {
        ProductListResponse external = response();
        when(cacheStore.findCatalog()).thenReturn(new ProductListResponse());
        when(databaseServiceProvider.getIfAvailable()).thenReturn(databaseService);
        when(databaseService.findCatalog()).thenReturn(new ProductListResponse());
        when(gestopagoProductService.obtenerYGuardarProductos()).thenReturn(external);

        ProductListResponse result = service.obtenerProductos();

        assertSame(external, result);
        verify(gestopagoProductService).obtenerYGuardarProductos();
    }

    @Test
    void gestopagoSuccessAndDatabaseFailureReturnsCustomCodeTwo() {
        when(cacheStore.findCatalog()).thenReturn(new ProductListResponse());
        when(databaseServiceProvider.getIfAvailable()).thenReturn(databaseService);
        when(databaseService.findCatalog()).thenReturn(new ProductListResponse());
        when(gestopagoProductService.obtenerYGuardarProductos()).thenThrow(
                new ProductIntegrationException(
                        ProductIntegrationErrorType.DATABASE_ERROR,
                        "database error"
                )
        );

        ProductIntegrationException exception = assertThrows(
                ProductIntegrationException.class,
                service::obtenerProductos
        );

        assertEquals(2, exception.getErrorType().getCode());
        verify(gestopagoProductService).obtenerYGuardarProductos();
    }

    @Test
    void concurrentCacheMissesInvokeGestopagoOnlyOnce() throws Exception {
        ProductListResponse external = response();
        AtomicReference<ProductListResponse> simulatedCache = new AtomicReference<>();
        when(cacheStore.findCatalog()).thenAnswer(invocation -> {
            ProductListResponse cached = simulatedCache.get();
            return cached == null ? new ProductListResponse() : cached;
        });
        when(databaseServiceProvider.getIfAvailable()).thenReturn(databaseService);
        when(databaseService.findCatalog()).thenReturn(new ProductListResponse());
        when(gestopagoProductService.obtenerYGuardarProductos()).thenAnswer(invocation -> {
            simulatedCache.set(external);
            return external;
        });

        int requests = 20;
        ExecutorService executor = Executors.newFixedThreadPool(requests);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<ProductListResponse>> futures = new ArrayList<>();
        try {
            for (int index = 0; index < requests; index++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return service.obtenerProductos();
                }));
            }
            start.countDown();

            for (Future<ProductListResponse> future : futures) {
                assertSame(external, future.get(5, TimeUnit.SECONDS));
            }
        } finally {
            executor.shutdownNow();
        }

        verify(gestopagoProductService, times(1)).obtenerYGuardarProductos();
        verify(databaseService, times(1)).findCatalog();
    }

    private ProductListResponse response() {
        ProductMessage message = new ProductMessage();
        message.setCodigo("01");
        message.setTexto("OK");

        ProductListResponse response = new ProductListResponse();
        response.setMensaje(message);
        ProductContainer container = new ProductContainer();
        container.setProductos(List.of(new com.proyecto.servicios.model.product.ProductDto()));
        response.setProductos(container);
        return response;
    }
}
