package com.proyecto.servicios.controller;

import com.proyecto.servicios.model.pago.PagoDtos.*;
import com.proyecto.servicios.service.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(value = "/api/pagos", produces = "application/json")
@ConditionalOnProperty(name = "app.database.enabled", havingValue = "true")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Pagos", description = "Solo CLIENTE, cuenta propia y segundo factor TOTP por operacion. Sin comision adicional.")
public class PagoController {
    public record VacioRequest() implements com.proyecto.servicios.model.SolicitudJson { }
    private final PagoService pagos;
    private final SegundoFactorService segundoFactor;
    public PagoController(PagoService pagos, SegundoFactorService segundoFactor) { this.pagos = pagos; this.segundoFactor = segundoFactor; }
    @ModelAttribute
    public void protegerComprobantes(jakarta.servlet.http.HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Pragma", "no-cache");
    }
    @PostMapping(value = "/segundo-factor/estado", consumes = "application/json")
    @Operation(summary = "Consulta el segundo factor del cliente sin revelar secretos")
    public com.proyecto.servicios.model.GenericResponse<EstadoFactorResponse> estadoFactor(@Valid @RequestBody VacioRequest request) {
        return com.proyecto.servicios.model.GenericResponse.exito("Estado del segundo factor", segundoFactor.estado());
    }
    @PostMapping(value = "/segundo-factor/preparar", consumes = "application/json")
    @Operation(summary = "Prepara TOTP con contrasena; reemplazar uno activo exige codigo actual o de recuperacion")
    public com.proyecto.servicios.model.GenericResponse<PrepararFactorResponse> prepararFactor(@Valid @RequestBody PrepararFactorRequest request) {
        return com.proyecto.servicios.model.GenericResponse.exito("Segundo factor preparado", segundoFactor.preparar(request));
    }
    @PostMapping(value = "/segundo-factor/activar", consumes = "application/json")
    @Operation(summary = "Confirma el primer codigo y devuelve codigos de recuperacion una sola vez")
    public com.proyecto.servicios.model.GenericResponse<ActivarFactorResponse> activarFactor(@Valid @RequestBody ActivarFactorRequest request) {
        return com.proyecto.servicios.model.GenericResponse.exito("Segundo factor activado", segundoFactor.activar(request));
    }
    @PostMapping(value = "/preparar", consumes = "application/json")
    @Operation(summary = "Valida y fija el pago sin cobrar; reutiliza solicitudId si se pierde la respuesta",
            description = "No permite cambiar un pago existente. monto es precio fijo en recargas/tarjetas o montoPago en servicios. "
                    + "Unidad, UPC, horaLocal y datos de seguridad los genera el backend. Tipos no documentados se rechazan sin retener.")
    public com.proyecto.servicios.model.GenericResponse<PagoResponse> preparar(@Valid @RequestBody PrepararRequest request) {
        return com.proyecto.servicios.model.GenericResponse.exito("Pago preparado", pagos.preparar(request));
    }
    @PostMapping(value = "/autorizar", consumes = "application/json")
    @Operation(summary = "Verifica y consume el codigo TOTP, retiene el importe y envia sendTx una sola vez",
            description = "01: aplicado. 02: Servicio no Disponible. Consultar datos.estado y datos.aviso: un pago PENDIENTE o REVISION no debe repetirse.")
    public Resultado autorizar(@Valid @RequestBody AutorizarRequest request) { return Resultado.de(pagos.autorizar(request)); }
    @PostMapping(value = "/consulta", consumes = "application/json")
    @Operation(summary = "Consulta un pago propio; no reenvia pagos ni inicia otra retencion")
    public Resultado consultar(@Valid @RequestBody ConsultaRequest request) { return Resultado.de(pagos.consultar(request)); }
    @PostMapping(value = "/cancelar", consumes = "application/json")
    @Operation(summary = "Cancela un pago solo si aun no fue enviado; no revierte pagos ya enviados")
    public Resultado cancelar(@Valid @RequestBody ConsultaRequest request) { return Resultado.de(pagos.cancelar(request)); }
}
