package com.proyecto.servicios.service;

import com.proyecto.servicios.client.ProductCatalogClient;
import com.proyecto.servicios.exception.ProductIntegrationException;
import com.proyecto.servicios.model.product.ProductContainer;
import com.proyecto.servicios.model.product.ProductListResponse;
import com.proyecto.servicios.model.product.ProductMessage;
import com.proyecto.servicios.service.Impl.GestopagoProductGateway;
import com.proyecto.servicios.service.Impl.GestoPagoAccessTokenProvider;
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
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class GestopagoProductGatewayTest {

    @Mock
    private ProductCatalogClient productCatalogClient;

    @Mock
    private GestoPagoAccessTokenProvider tokenProvider;

    private GestopagoProductGateway gateway;

    @BeforeEach
    void setUp() {
        when(tokenProvider.obtenerToken()).thenReturn("fixture-token");
        gateway = new GestopagoProductGateway(productCatalogClient, tokenProvider, Duration.ofSeconds(30));
    }

    @Test
    void returnsSuccessfulCatalog() {
        ProductListResponse expected = response("01");
        when(productCatalogClient.getProductList("Bearer fixture-token")).thenReturn(ResponseEntity.ok(expected));

        assertSame(expected, gateway.fetchCatalog());
    }

    @Test
    void unavailableGestopagoReturnsCodeOne() {
        FeignException exception = mock(FeignException.class);
        when(exception.status()).thenReturn(503);
        when(productCatalogClient.getProductList("Bearer fixture-token")).thenThrow(exception);

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
        when(productCatalogClient.getProductList("Bearer fixture-token")).thenThrow(exception);

        ProductIntegrationException result = assertThrows(
                ProductIntegrationException.class,
                gateway::fetchCatalog
        );

        assertEquals(1, result.getErrorType().getCode());
    }

    @Test
    void invalidPayloadReturnsCodeTwo() {
        ProductListResponse invalid = new ProductListResponse();
        when(productCatalogClient.getProductList("Bearer fixture-token")).thenReturn(ResponseEntity.ok(invalid));

        ProductIntegrationException result = assertThrows(
                ProductIntegrationException.class,
                gateway::fetchCatalog
        );

        assertEquals(2, result.getErrorType().getCode());
    }

    @Test
    void peticionesConcurrentesDuranteCooldownConservanElErrorSinRepetirLaConsultaExterna() throws Exception {
        when(productCatalogClient.getProductList("Bearer fixture-token"))
                .thenReturn(ResponseEntity.ok(new ProductListResponse()));
        ProductIntegrationException initial = assertThrows(ProductIntegrationException.class, gateway::fetchCatalog);
        var tasks = IntStream.range(0, 32).mapToObj(index -> (Callable<Void>) () -> {
            ProductIntegrationException result = assertThrows(ProductIntegrationException.class, gateway::fetchCatalog);
            assertEquals(initial.getErrorType(), result.getErrorType());
            assertEquals(initial.getMessage(), result.getMessage());
            return null;
        }).toList();
        var executor = Executors.newFixedThreadPool(8);
        try {
            for (var result : executor.invokeAll(tasks, 15, TimeUnit.SECONDS)) {
                result.get(1, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }
        verify(productCatalogClient, times(1)).getProductList("Bearer fixture-token");
        verify(tokenProvider, times(1)).obtenerToken();
    }

    @Test
    void sinCooldownPuedeRecuperarseDelFalloEnLaSiguienteConsulta() {
        gateway = new GestopagoProductGateway(productCatalogClient, tokenProvider, Duration.ZERO);
        ProductListResponse expected = response("01");
        when(productCatalogClient.getProductList("Bearer fixture-token"))
                .thenReturn(ResponseEntity.ok(new ProductListResponse()))
                .thenReturn(ResponseEntity.ok(expected));

        assertThrows(ProductIntegrationException.class, gateway::fetchCatalog);
        assertSame(expected, gateway.fetchCatalog());
        verify(productCatalogClient, times(2)).getProductList("Bearer fixture-token");
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
