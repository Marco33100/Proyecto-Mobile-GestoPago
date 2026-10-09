package com.proyecto.servicios.model.cliente;

import java.util.UUID;

public record DomicilioResponse(
        UUID id,
        String calle,
        String numeroExterior,
        String numeroInterior,
        String colonia,
        String municipio,
        String estado,
        String codigoPostal,
        String pais
) {
}
