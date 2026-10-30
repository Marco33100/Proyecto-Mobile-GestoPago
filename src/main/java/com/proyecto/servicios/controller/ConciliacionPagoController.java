package com.proyecto.servicios.controller;

import com.proyecto.servicios.model.GenericResponse;
import com.proyecto.servicios.model.cliente.*;
import com.proyecto.servicios.model.pago.ConciliacionDtos.*;
import com.proyecto.servicios.service.ConciliacionPagoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(value="/api/pagos/conciliacion",produces="application/json",consumes="application/json")
@ConditionalOnProperty(name="app.database.enabled",havingValue="true")
@SecurityRequirement(name="bearerAuth")
@Tag(name="Conciliacion de pagos",description="Solo ejecutivos expresamente habilitados; evidencia y doble aprobacion. No envia sendTx.")
public class ConciliacionPagoController {
    private final ConciliacionPagoService conciliacion;
    public ConciliacionPagoController(ConciliacionPagoService conciliacion) { this.conciliacion=conciliacion; }
    @ModelAttribute
    public void noAlmacenar(jakarta.servlet.http.HttpServletResponse response) {
        response.setHeader("Cache-Control","no-store"); response.setHeader("Pragma","no-cache");
    }
    @PostMapping("/pendientes")
    @Operation(summary="Lista paginada de pagos en REVISION, sin liberar ni reenviar operaciones")
    public GenericResponse<PaginaResponse<Caso>> pendientes(@Valid @RequestBody PaginacionRequest request) {
        var pagina=conciliacion.pendientes(request);
        return GenericResponse.consulta("Casos de conciliacion consultados",pagina,!pagina.contenido().isEmpty());
    }
    @PostMapping("/propuestas")
    @Operation(summary="Registra evidencia verificada y propuesta de resolucion; no modifica el saldo")
    public GenericResponse<Propuesta> proponer(@Valid @RequestBody ProponerRequest request) {
        return GenericResponse.exito("Propuesta registrada, pendiente de aprobacion independiente",conciliacion.proponer(request));
    }
    @PostMapping("/propuestas/consulta")
    @Operation(summary="Consulta evidencia restringida para revisar una propuesta")
    public GenericResponse<Propuesta> consultar(@Valid @RequestBody PropuestaRequest request) {
        return GenericResponse.exito("Propuesta consultada",conciliacion.consultar(request));
    }
    @PostMapping("/propuestas/aprobar")
    @Operation(summary="Segundo operador aprueba y liquida una sola vez el importe retenido",
            description="Exige evidencia oficial revisada, mismo UPC/importe/version y un operador distinto del proponente. No determina la verdad del proveedor por si mismo.")
    public GenericResponse<Propuesta> aprobar(@Valid @RequestBody DecisionRequest request) {
        return GenericResponse.exito("Conciliacion aprobada",conciliacion.aprobar(request));
    }
    @PostMapping("/propuestas/rechazar")
    @Operation(summary="Rechaza o retira una propuesta sin modificar el saldo ni la retencion")
    public GenericResponse<Propuesta> rechazar(@Valid @RequestBody DecisionRequest request) {
        return GenericResponse.exito("Propuesta rechazada; el pago conserva su estado",conciliacion.rechazar(request));
    }
}
