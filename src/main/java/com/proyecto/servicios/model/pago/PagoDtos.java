package com.proyecto.servicios.model.pago;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.proyecto.servicios.model.SolicitudJson;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class PagoDtos {
    private PagoDtos() { }
    public record PrepararRequest(
            @NotNull UUID solicitudId,
            @NotBlank @Size(max = 24) String numeroCuenta,
            @NotNull @Positive Integer idServicio,
            @NotNull @Positive Integer idProducto,
            @NotNull @Positive Integer tipoFront,
            @NotNull @DecimalMin("0.01") @Digits(integer = 17, fraction = 2) BigDecimal monto,
            @Pattern(regexp = "[0-9]{10,20}") String telefono,
            @Size(min = 1, max = 100) String referencia
    ) implements SolicitudJson {
        @Override public String toString() { return "PrepararRequest[protegido]"; }
    }
    public record AutorizarRequest(@NotNull UUID pagoId, @NotBlank @Pattern(regexp = "[0-9]{6}") String codigo) implements SolicitudJson {
        @Override public String toString() { return "AutorizarRequest[protegido]"; }
    }
    public record ConsultaRequest(@NotNull UUID pagoId) implements SolicitudJson { }
    public record PrepararFactorRequest(
            @NotBlank @Size(max = 72) String contrasena,
            @Pattern(regexp = "[0-9]{6}") String codigoActual,
            @Pattern(regexp = "[A-F0-9]{8}(?:-[A-F0-9]{8}){3}") String codigoRecuperacion
    ) implements SolicitudJson {
        @Override public String toString() { return "PrepararFactorRequest[protegido]"; }
    }
    public record ActivarFactorRequest(@NotBlank @Pattern(regexp = "[0-9]{6}") String codigo) implements SolicitudJson {
        @Override public String toString() { return "ActivarFactorRequest[protegido]"; }
    }
    public record EstadoFactorResponse(boolean activo) { }
    public record PrepararFactorResponse(String secreto, String uri, Instant venceEn) {
        @Override public String toString() { return "PrepararFactorResponse[protegido]"; }
    }
    public record ActivarFactorResponse(boolean activo, java.util.List<String> codigosRecuperacion) {
        @Override public String toString() { return "ActivarFactorResponse[protegido]"; }
    }
    public record PagoResponse(
            UUID pagoId, String estado, String upc, String numeroCuenta,
            String concepto, BigDecimal monto, BigDecimal comisionCliente,
            Instant venceEn, String idTransaccion, String autorizacion,
            String pin, String leyenda, String aviso, String telefono, String referencia,
            BigDecimal saldoReferencia, Instant consultaReferenciaEn, String reglaImporteReferencia,
            boolean autorizacionDisponible
    ) {
        @Override public String toString() { return "PagoResponse[protegido]"; }
    }
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Resultado(String codigo, String mensaje, PagoResponse datos) {
        public static Resultado de(PagoResponse pago) {
            boolean aplicado = "APLICADO".equals(pago.estado());
            String mensaje = switch (pago.estado()) {
                case "APLICADO" -> "Pago realizado correctamente";
                case "PREPARADO" -> "Pago preparado. Revisa los datos antes de autorizar.";
                case "ENVIADO", "PENDIENTE" -> "Estamos verificando tu pago. Consulta su estado antes de intentar nuevamente.";
                case "REVISION" -> "El pago esta en revision. Consulta su estado; no lo vuelvas a enviar.";
                case "RECHAZADO" -> "El pago no fue aplicado. Consulta el detalle de la operacion.";
                case "EXPIRADO" -> "La preparacion del pago vencio. Prepara una nueva solicitud.";
                default -> "No es posible procesar el pago en este momento.";
            };
            return new Resultado(aplicado ? "01" : "02", mensaje, pago);
        }
        public static Resultado error() { return new Resultado("02", "Servicio no Disponible", null); }
        public static Resultado error(String mensaje) { return new Resultado("02", mensaje, null); }
    }
}
