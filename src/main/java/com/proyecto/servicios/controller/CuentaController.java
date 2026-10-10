package com.proyecto.servicios.controller;

import com.proyecto.servicios.model.cliente.CuentaResponse;
import com.proyecto.servicios.model.cliente.PaginaResponse;
import com.proyecto.servicios.model.cliente.SaldoCuentaResponse;
import com.proyecto.servicios.model.cliente.ActualizarEstadoCuentaRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import static com.proyecto.servicios.support.CuentaDatos.NUMERO_CUENTA_REGEX;
import static com.proyecto.servicios.support.CuentaDatos.NUMERO_CUENTA_MENSAJE;
import static com.proyecto.servicios.support.PaginacionDatos.PAGINA_MINIMA;
import static com.proyecto.servicios.support.PaginacionDatos.TAMANIO_MINIMO;
import static com.proyecto.servicios.support.PaginacionDatos.TAMANIO_MAXIMO;
import com.proyecto.servicios.service.CuentaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
    @Operation(summary = "Consulta una cuenta por su numero",
            description = "CLIENTE solo consulta cuentas propias; EJECUTIVO conserva el acceso administrativo. "
                    + "Para CLIENTE, una cuenta ajena devuelve 404 igual que una inexistente.")
    public CuentaResponse consultarPorNumeroCuenta(
            @PathVariable @Pattern(regexp = NUMERO_CUENTA_REGEX, message = NUMERO_CUENTA_MENSAJE) String numeroCuenta) {
        return cuentaService.consultarPorNumeroCuenta(numeroCuenta);
    }

    @GetMapping("/activas")
    @Operation(summary = "Consulta las cuentas activas de forma paginada")
    public PaginaResponse<CuentaResponse> consultarActivas(
            @RequestParam(defaultValue = "0") @Min(PAGINA_MINIMA) int pagina,
            @RequestParam(defaultValue = "20") @Min(TAMANIO_MINIMO) @Max(TAMANIO_MAXIMO) int tamanio) {
        return cuentaService.consultarActivas(pagina, tamanio);
    }

    @GetMapping("/mias")
    @Operation(summary = "Consulta las cuentas del cliente autenticado",
            description = "Solo CLIENTE. Incluye cuentas propias activas e inactivas. "
                    + "La identidad se obtiene de la sesion, no de parametros enviados por el cliente.")
    public PaginaResponse<CuentaResponse> consultarPropias(
            @RequestParam(defaultValue = "0") @Min(PAGINA_MINIMA) int pagina,
            @RequestParam(defaultValue = "20") @Min(TAMANIO_MINIMO) @Max(TAMANIO_MAXIMO) int tamanio) {
        return cuentaService.consultarPropias(pagina, tamanio);
    }

    @DeleteMapping("/{numeroCuenta}")
    @Operation(summary = "Baja logica de la cuenta (compatibilidad); preferir PATCH con activa=false", deprecated = true)
    public CuentaResponse desactivar(
            @PathVariable @Pattern(regexp = NUMERO_CUENTA_REGEX, message = NUMERO_CUENTA_MENSAJE) String numeroCuenta) {
        return cuentaService.desactivar(numeroCuenta);
    }

    @PatchMapping(value = "/{numeroCuenta}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Desactiva una cuenta enviando solo activa=false",
            description = "El numero de cuenta se envia una sola vez en la ruta. No se permite modificar "
                    + "saldo, titular, identificadores ni reactivar. Las propiedades no enviadas se conservan. "
                    + "El estado no admite null; {} y activa=null devuelven 400 sin cambios. "
                    + "Repetir la baja no genera otra escritura.")
    public CuentaResponse actualizarEstado(
            @PathVariable @Pattern(regexp = NUMERO_CUENTA_REGEX, message = NUMERO_CUENTA_MENSAJE) String numeroCuenta,
            @Valid @RequestBody ActualizarEstadoCuentaRequest request) {
        return cuentaService.actualizarEstado(numeroCuenta, request);
    }

    @GetMapping("/{numeroCuenta}/saldo")
    @Operation(summary = "Consulta el saldo disponible de una cuenta",
            description = "CLIENTE solo consulta saldos de cuentas propias; EJECUTIVO conserva el acceso administrativo.")
    public SaldoCuentaResponse consultarSaldo(
            @PathVariable @Pattern(regexp = NUMERO_CUENTA_REGEX, message = NUMERO_CUENTA_MENSAJE) String numeroCuenta) {
        return cuentaService.consultarSaldo(numeroCuenta);
    }
}
