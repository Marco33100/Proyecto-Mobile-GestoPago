package com.proyecto.servicios.service;

import com.proyecto.servicios.exception.ProductCacheException;
import com.proyecto.servicios.exception.ProductIntegrationException;
import com.proyecto.servicios.model.product.ProductContainer;
import com.proyecto.servicios.model.product.ProductDto;
import com.proyecto.servicios.model.product.ProductListResponse;
import com.proyecto.servicios.model.product.ProductMessage;
import com.proyecto.servicios.model.product.ProductSyncResult;
import com.proyecto.servicios.service.Impl.GestopagoProductGateway;
import com.proyecto.servicios.service.Impl.GestopagoProductServiceImpl;
import com.proyecto.servicios.service.Impl.ProductCacheStore;
import com.proyecto.servicios.service.Impl.ProductDatabaseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GestopagoProductServiceImplTest {

    @Mock
    private GestopagoProductGateway gateway;
    @Mock
    private ObjectProvider<ProductDatabaseService> databaseServiceProvider;
    @Mock
    private ProductDatabaseService databaseService;
    @Mock
    private ProductCacheStore cacheStore;

    private GestopagoProductServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new GestopagoProductServiceImpl(gateway, databaseServiceProvider, cacheStore);
    }

    @Test
    void directIntegrationPersistsPostgresThenRedisAndReturnsResponse() {
        ProductListResponse external = response("Gestopago");
        when(gateway.fetchCatalog()).thenReturn(external);
        when(databaseServiceProvider.getIfAvailable()).thenReturn(databaseService);
        when(databaseService.replaceIfLarger(external)).thenReturn(new ProductSyncResult(true, 0, 900));

        ProductListResponse result = service.obtenerYGuardarProductos();

        assertThat(result).isSameAs(external);
        verify(databaseService).replaceIfLarger(external);
        verify(cacheStore).replaceCatalog(external);
    }

    @Test
    void keepsCurrentCatalogWhenExternalSizeIsNotLarger() {
        ProductListResponse external = response("Gestopago");
        ProductListResponse current = response("PostgreSQL");
        when(gateway.fetchCatalog()).thenReturn(external);
        when(databaseServiceProvider.getIfAvailable()).thenReturn(databaseService);
        when(databaseService.replaceIfLarger(external)).thenReturn(new ProductSyncResult(false, 900, 900));
        when(databaseService.findCatalog()).thenReturn(current);

        ProductListResponse result = service.obtenerYGuardarProductos();

        assertThat(result).isSameAs(current);
        verify(cacheStore).replaceCatalog(current);
    }

    @Test
    void redisFailureDoesNotDiscardCatalogAlreadyStoredInPostgres() {
        ProductListResponse external = response("Gestopago");
        when(gateway.fetchCatalog()).thenReturn(external);
        when(databaseServiceProvider.getIfAvailable()).thenReturn(databaseService);
        when(databaseService.replaceIfLarger(external)).thenReturn(new ProductSyncResult(true, 0, 900));
        doThrow(new ProductCacheException("redis", new RuntimeException()))
                .when(cacheStore).replaceCatalog(external);

        assertThat(service.obtenerYGuardarProductos()).isSameAs(external);
    }

    @Test
    void failsWithCodeTwoWhenPostgresIsNotEnabled() {
        when(gateway.fetchCatalog()).thenReturn(response("Gestopago"));
        when(databaseServiceProvider.getIfAvailable()).thenReturn(null);

        assertThatThrownBy(service::obtenerYGuardarProductos)
                .isInstanceOf(ProductIntegrationException.class)
                .satisfies(exception -> assertThat(
                        ((ProductIntegrationException) exception).getErrorType().getCode()
                ).isEqualTo(2));
    }

    private ProductListResponse response(String source) {
        ProductMessage message = new ProductMessage();
        message.setCodigo("01");
        message.setTexto(source);
        ProductListResponse response = new ProductListResponse();
        response.setMensaje(message);
        ProductContainer container = new ProductContainer();
        container.setProductos(List.of(new ProductDto()));
        response.setProductos(container);
        return response;
    }
}
