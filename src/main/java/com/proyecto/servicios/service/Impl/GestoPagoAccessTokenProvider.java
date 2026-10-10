package com.proyecto.servicios.service.Impl;

import com.proyecto.servicios.config.ProductServiceProperties;
import com.proyecto.servicios.exception.GestoPagoTokenRejectedException;
import com.proyecto.servicios.exception.ProductIntegrationErrorType;
import com.proyecto.servicios.exception.ProductIntegrationException;
import com.proyecto.servicios.service.GestoPagoTokenService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class GestoPagoAccessTokenProvider {
    private final ProductServiceProperties properties;
    private final ObjectProvider<GestoPagoTokenService> tokenService;
    private final boolean automatic;

    public GestoPagoAccessTokenProvider(ProductServiceProperties properties,
                                       ObjectProvider<GestoPagoTokenService> tokenService,
                                       @Value("${gestopago.auth.enabled:false}") boolean automatic) {
        this.properties = properties;
        this.tokenService = tokenService;
        this.automatic = automatic;
    }

    public String obtenerToken() {
        if (automatic) {
            return automaticService().obtenerTokenVigente();
        }
        if (!StringUtils.hasText(properties.getBearerToken())) {
            throw new ProductIntegrationException(ProductIntegrationErrorType.GESTOPAGO_UNAVAILABLE,
                    "No se configuro la autenticacion de Gestopago");
        }
        return properties.getBearerToken().trim();
    }

    public String renovarTrasRechazo(String rejectedToken) {
        if (!automatic) {
            throw new GestoPagoTokenRejectedException();
        }
        // No recurrir al token fijo si falla la renovacion automatica.
        return automaticService().renovarTokenRechazado(rejectedToken);
    }

    private GestoPagoTokenService automaticService() {
        GestoPagoTokenService service = tokenService.getIfAvailable();
        if (service == null) {
            throw new ProductIntegrationException(ProductIntegrationErrorType.GESTOPAGO_UNAVAILABLE,
                    "La autenticacion automatica de Gestopago requiere la base de datos habilitada");
        }
        return service;
    }
}
