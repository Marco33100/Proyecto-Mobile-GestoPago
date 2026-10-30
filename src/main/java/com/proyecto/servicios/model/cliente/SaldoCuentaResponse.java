package com.proyecto.servicios.model.cliente;

import java.math.BigDecimal;

public record SaldoCuentaResponse(String numeroCuenta, BigDecimal saldo, BigDecimal saldoDisponible, BigDecimal saldoRetenido) {
    public SaldoCuentaResponse(String numeroCuenta, BigDecimal saldo) { this(numeroCuenta, saldo, saldo, BigDecimal.ZERO); }
}
