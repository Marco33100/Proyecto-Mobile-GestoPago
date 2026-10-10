package com.proyecto.servicios.service;

import com.proyecto.servicios.config.ProductServiceProperties;
import com.proyecto.servicios.exception.ProductIntegrationException;
import com.proyecto.servicios.exception.GestoPagoTokenRejectedException;
import com.proyecto.servicios.service.Impl.GestoPagoAccessTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class GestoPagoAccessTokenProviderTest {
    private GestoPagoAccessTokenProvider provider(boolean automatic, GestoPagoTokenService service, String fixed) {
        var properties = new ProductServiceProperties();
        properties.setBearerToken(fixed);
        var factory = new StaticListableBeanFactory();
        if (service != null) { factory.addBean("tokenService", service); }
        return new GestoPagoAccessTokenProvider(properties, factory.getBeanProvider(GestoPagoTokenService.class), automatic);
    }

    @Test
    void modoManualNoAutenticaNiRenueva() {
        var service = mock(GestoPagoTokenService.class);
        var provider = provider(false, service, " fixed-fixture ");
        assertThat(provider.obtenerToken()).isEqualTo("fixed-fixture");
        assertThatThrownBy(() -> provider.renovarTrasRechazo("fixed-fixture"))
                .isInstanceOf(GestoPagoTokenRejectedException.class);
        verifyNoInteractions(service);
    }

    @Test
    void modoAutomaticoNoUsaTokenFijoSiFallaElProveedor() {
        var service = mock(GestoPagoTokenService.class);
        when(service.obtenerTokenVigente()).thenThrow(new ProductIntegrationException(
                com.proyecto.servicios.exception.ProductIntegrationErrorType.GESTOPAGO_UNAVAILABLE, "failure"));
        assertThatThrownBy(provider(true, service, "fixed-fixture")::obtenerToken)
                .isInstanceOf(ProductIntegrationException.class);
    }

    @Test
    void automaticoConsultaYRenuevaConElServicio() {
        var service = mock(GestoPagoTokenService.class);
        when(service.obtenerTokenVigente()).thenReturn("old");
        when(service.renovarTokenRechazado("old")).thenReturn("new");
        var provider = provider(true, service, "ignored");
        assertThat(provider.obtenerToken()).isEqualTo("old");
        assertThat(provider.renovarTrasRechazo("old")).isEqualTo("new");
    }

    @Test
    void ausenciaDeServicioAutomaticoNoUsaTokenFijo() {
        assertThatThrownBy(provider(true, null, "fixed-fixture")::obtenerToken)
                .isInstanceOf(ProductIntegrationException.class);
    }

    @Test
    void modoManualSinTokenDevuelveErrorControlado() {
        assertThatThrownBy(provider(false, null, " ")::obtenerToken)
                .isInstanceOf(ProductIntegrationException.class);
    }
}
