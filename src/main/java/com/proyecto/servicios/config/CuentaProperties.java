package com.proyecto.servicios.config;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;

@Component
@Validated
@ConfigurationProperties(prefix = "cliente.cuenta")
public class CuentaProperties {

    @NotNull
    @DecimalMin(value = "0.00")
    @Digits(integer = 17, fraction = 2)
    private BigDecimal saldoInicial = BigDecimal.ZERO;

    public BigDecimal getSaldoInicial() {
        return saldoInicial;
    }

    public void setSaldoInicial(BigDecimal saldoInicial) {
        this.saldoInicial = saldoInicial;
    }
}
