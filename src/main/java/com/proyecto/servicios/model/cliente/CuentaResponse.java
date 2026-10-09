package com.proyecto.servicios.model.cliente;

import java.math.BigDecimal;
import java.util.UUID;

public record CuentaResponse(
        UUID id,
        String numeroCuenta,
        BigDecimal saldo,
        boolean activa
) {
}
