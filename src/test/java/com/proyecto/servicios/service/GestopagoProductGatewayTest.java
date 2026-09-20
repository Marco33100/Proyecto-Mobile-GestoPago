package com.proyecto.servicios.service;

import com.proyecto.servicios.client.ProductCatalogClient;
import com.proyecto.servicios.exception.ProductIntegrationException;
import com.proyecto.servicios.model.product.ProductContainer;
import com.proyecto.servicios.model.product.ProductListResponse;
import com.proyecto.servicios.model.product.ProductMessage;
import com.proyecto.servicios.service.Impl.GestopagoProductGateway;
import feign.FeignException;
import feign.RetryableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.net.SocketTimeoutException;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GestopagoProductGatewayTest {

    @Mock
    private ProductCatalogClient productCatalogClient;

    private GestopagoProductGateway gateway;

    @BeforeEach
    void setUp() {
        gateway = new GestopagoProductGateway(productCatalogClient, Duration.ofSeconds(30));
    }

    @Test
    void returnsSuccessfulCatalog() {
        ProductListResponse expected = response("01");
        when(productCatalogClient.getProductList()).thenReturn(ResponseEntity.ok(expected));

        assertSame(expected, gateway.fetchCatalog());
    }

    @Test
    void unavailableGestopagoReturnsCodeOne() {
        FeignException exception = mock(FeignException.class);
        when(exception.status()).thenReturn(503);
        when(productCatalogClient.getProductList()).thenThrow(exception);

        ProductIntegrationException result = assertThrows(
                ProductIntegrationException.class,
                gateway::fetchCatalog
        );

        assertEquals(1, result.getErrorType().getCode());
    }

    @Test
    void timeoutReturnsCodeOne() {
        RetryableException exception = mock(RetryableException.class);
        when(exception.getCause()).thenReturn(new SocketTimeoutException("timeout"));
        when(productCatalogClient.getProductList()).thenThrow(exception);

        ProductIntegrationException result = assertThrows(
                ProductIntegrationException.class,
                gateway::fetchCatalog
        );

        assertEquals(1, result.getErrorType().getCode());
    }

    @Test
    void invalidPayloadReturnsCodeTwo() {
        ProductListResponse invalid = new ProductListResponse();
        when(productCatalogClient.getProductList()).thenReturn(ResponseEntity.ok(invalid));

        ProductIntegrationException result = assertThrows(
                ProductIntegrationException.class,
                gateway::fetchCatalog
        );

        assertEquals(2, result.getErrorType().getCode());
    }

    private ProductListResponse response(String code) {
        ProductMessage message = new ProductMessage();
        message.setCodigo(code);
        message.setTexto("respuesta de prueba");

        ProductListResponse response = new ProductListResponse();
        response.setMensaje(message);
        response.setProductos(new ProductContainer());
        return response;
    }
}
