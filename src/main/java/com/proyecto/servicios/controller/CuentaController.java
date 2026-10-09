package com.proyecto.servicios.controller;

import com.proyecto.servicios.model.cliente.CuentaResponse;
import com.proyecto.servicios.model.cliente.PaginaResponse;
import com.proyecto.servicios.model.cliente.SaldoCuentaResponse;
import com.proyecto.servicios.service.CuentaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.validation.annotation.Validated;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping(value = "/api/cuentas", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Cuentas", description = "Consulta de cuentas bancarias y saldos")
@SecurityRequirement(name = "bearerAuth")
@ConditionalOnProperty(name = "app.database.enabled", havingValue = "true")
public class CuentaController {
    private final CuentaService cuentaService;

    @Autowired
    public CuentaController(CuentaService cuentaService) {
        this.cuentaService = cuentaService;
    }

    @GetMapping("/{numeroCuenta}")
    @Operation(summary = "Consulta una cuenta por su numero")
    public CuentaResponse consultarPorNumeroCuenta(@PathVariable @Size(min = 1, max = 24) String numeroCuenta) {
        return cuentaService.consultarPorNumeroCuenta(numeroCuenta);
    }

    @GetMapping("/activas")
    @Operation(summary = "Consulta las cuentas activas de forma paginada")
    public PaginaResponse<CuentaResponse> consultarActivas(
            @RequestParam(defaultValue = "0") @Min(0) int pagina,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int tamanio) {
        return cuentaService.consultarActivas(pagina, tamanio);
    }

    @DeleteMapping("/{numeroCuenta}")
    @Operation(summary = "Desactiva solamente la cuenta, sin bloquear el acceso del cliente")
    public CuentaResponse desactivar(@PathVariable @Size(min = 1, max = 24) String numeroCuenta) {
        return cuentaService.desactivar(numeroCuenta);
    }

    @GetMapping("/{numeroCuenta}/saldo")
    @Operation(summary = "Consulta el saldo disponible de una cuenta")
    public SaldoCuentaResponse consultarSaldo(@PathVariable @Size(min = 1, max = 24) String numeroCuenta) {
        CuentaResponse cuenta = cuentaService.consultarPorNumeroCuenta(numeroCuenta);
        return new SaldoCuentaResponse(cuenta.numeroCuenta(), cuenta.saldo());
    }
}
