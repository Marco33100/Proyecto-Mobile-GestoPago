package com.proyecto.servicios.service;

import com.proyecto.servicios.entity.gestopago.GestoPagoToken;

import java.util.Optional;

public interface GestoPagoTokenService {

    void renovarToken();

    String obtenerTokenVigente();

    String renovarTokenRechazado(String tokenRechazado);

    Optional<GestoPagoToken> obtenerTokenActivo(Integer idDistribuidor, String codigoDispositivo);
}
