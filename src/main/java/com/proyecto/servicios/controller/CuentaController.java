package com.proyecto.servicios.controller;

import com.proyecto.servicios.model.cliente.CuentaResponse;
import com.proyecto.servicios.model.cliente.PaginaResponse;
import com.proyecto.servicios.model.cliente.SaldoCuentaResponse;
import com.proyecto.servicios.model.cliente.ModificarEstadoCuentaRequest;
import com.proyecto.servicios.model.cliente.PaginacionRequest;
import com.proyecto.servicios.model.cliente.NumeroCuentaRequest;
import com.proyecto.servicios.model.GenericResponse;
import jakarta.validation.Valid;
import com.proyecto.servicios.service.CuentaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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

    @PostMapping(value = "/consulta", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Consulta una cuenta por su numero",
            description = "CLIENTE solo consulta cuentas propias; EJECUTIVO conserva el acceso administrativo. "
                    + "Para CLIENTE, una cuenta ajena devuelve 404 igual que una inexistente.")
    public GenericResponse<CuentaResponse> consultarPorNumeroCuenta(
            @Valid @RequestBody NumeroCuentaRequest request) {
        return GenericResponse.exito("Cuenta consultada correctamente", cuentaService.consultarPorNumeroCuenta(request.numeroCuenta()));
    }

    @PostMapping(value = "/activas/consulta", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Consulta las cuentas activas de forma paginada")
    public GenericResponse<PaginaResponse<CuentaResponse>> consultarActivas(
            @Valid @RequestBody PaginacionRequest request) {
        var resultado = cuentaService.consultarActivas(request.getPagina(), request.getTamanio());
        return GenericResponse.consulta("Cuentas consultadas correctamente", resultado, !resultado.contenido().isEmpty());
    }

    @PostMapping(value = "/mias/consulta", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Consulta las cuentas del cliente autenticado",
            description = "Solo CLIENTE. Incluye cuentas propias activas e inactivas. "
                    + "La identidad se obtiene de la sesion, no de parametros enviados por el cliente.")
    public GenericResponse<PaginaResponse<CuentaResponse>> consultarPropias(
            @Valid @RequestBody PaginacionRequest request) {
        var resultado = cuentaService.consultarPropias(request.getPagina(), request.getTamanio());
        return GenericResponse.consulta("Cuentas consultadas correctamente", resultado, !resultado.contenido().isEmpty());
    }

    @DeleteMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Baja logica de la cuenta (compatibilidad); preferir PATCH con activa=false", deprecated = true)
    public GenericResponse<CuentaResponse> desactivar(
            @Valid @RequestBody NumeroCuentaRequest request) {
        return GenericResponse.exito("Cuenta desactivada correctamente", cuentaService.desactivar(request.numeroCuenta()));
    }

    @PatchMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Desactiva una cuenta enviando solo activa=false",
            description = "El numero de cuenta se envia una sola vez en el cuerpo y cambios solo admite activa=false. No se permite modificar "
                    + "saldo, titular, identificadores ni reactivar. Las propiedades no enviadas se conservan. "
                    + "El estado no admite null; {} y activa=null devuelven 400 sin cambios. "
                    + "Repetir la baja no genera otra escritura.")
    public GenericResponse<CuentaResponse> actualizarEstado(
            @Valid @RequestBody ModificarEstadoCuentaRequest request) {
        return GenericResponse.exito("Estado de cuenta actualizado correctamente",
                cuentaService.actualizarEstado(request.numeroCuenta(), request.cambios()));
    }

    @PostMapping(value = "/saldo/consulta", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Consulta el saldo disponible de una cuenta",
            description = "CLIENTE solo consulta saldos de cuentas propias; EJECUTIVO conserva el acceso administrativo.")
    public GenericResponse<SaldoCuentaResponse> consultarSaldo(
            @Valid @RequestBody NumeroCuentaRequest request) {
        return GenericResponse.exito("Saldo consultado correctamente", cuentaService.consultarSaldo(request.numeroCuenta()));
    }
}
