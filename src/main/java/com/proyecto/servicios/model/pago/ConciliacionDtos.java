package com.proyecto.servicios.model.pago;

import com.proyecto.servicios.model.SolicitudJson;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class ConciliacionDtos {
    private ConciliacionDtos() { }
    public enum Resultado { APLICADO, NO_APLICADO_DEFINITIVO, DUPLICADO_YA_CONTABILIZADO }
    public record ProponerRequest(
            @NotNull UUID propuestaId, @NotNull UUID pagoId, @NotNull @PositiveOrZero Long versionPago,
            @NotBlank @Pattern(regexp = "[A-Z0-9]{10}") String upc,
            @NotNull @DecimalMin("0.01") @Digits(integer = 17, fraction = 2) BigDecimal monto,
            @NotNull Resultado resultado,
            @Pattern(regexp = "[0-9]{1,60}") String idTransaccion,
            @Pattern(regexp = "[0-9]{1,60}") String autorizacion,
            @NotBlank @Size(max = 100) String folioProveedor,
            @NotBlank @Size(min = 20, max = 16000) String evidenciaProveedor,
            @NotBlank @Size(min = 10, max = 1000) String motivo
    ) implements SolicitudJson {
        @Override public String toString() { return "ProponerConciliacionRequest[protegido]"; }
    }
    public record PropuestaRequest(@NotNull UUID propuestaId) implements SolicitudJson { }
    public record DecisionRequest(@NotNull UUID propuestaId, @NotBlank @Size(min = 10, max = 1000) String observacion)
            implements SolicitudJson {
        @Override public String toString() { return "DecisionConciliacionRequest[protegido]"; }
    }
    public record Caso(UUID pagoId, String estado, String motivoRevision, String upc, BigDecimal monto,
                       long versionPago, Instant enviadoEn) { }
    public record Propuesta(UUID propuestaId, UUID pagoId, String estado, Resultado resultado,
                            UUID propuestoPor, UUID decididoPor, Instant creadoEn, Instant venceEn,
                            ProponerRequest detalle) {
        @Override public String toString() { return "PropuestaConciliacion[protegida]"; }
    }
}
