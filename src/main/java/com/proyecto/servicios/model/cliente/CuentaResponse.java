package com.proyecto.servicios.model.cliente;

import java.math.BigDecimal;
import java.util.UUID;

public record CuentaResponse(
        UUID id,
        String numeroCuenta,
        BigDecimal saldo,
        boolean activa,
        BigDecimal saldoDisponible,
        BigDecimal saldoRetenido
) {
    public CuentaResponse(UUID id, String numeroCuenta, BigDecimal saldo, boolean activa) {
        this(id, numeroCuenta, saldo, activa, saldo, BigDecimal.ZERO);
    }
}
