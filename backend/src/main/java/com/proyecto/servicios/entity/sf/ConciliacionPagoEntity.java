package com.proyecto.servicios.entity.sf;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "payment_reconciliations")
@Getter @Setter @NoArgsConstructor
public class ConciliacionPagoEntity {
    public enum Estado { PROPUESTA, APROBADA, RECHAZADA }
    @Id private UUID id;
    @Column(name = "pago_id", nullable = false) private UUID pagoId;
    @Column(name = "version_pago", nullable = false) private long versionPago;
    @Column(name = "propuesto_por", nullable = false) private UUID propuestoPor;
    @Column(name = "decidido_por") private UUID decididoPor;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 12) private Estado estado;
    @JsonIgnore @Column(name = "contenido_cifrado", nullable = false, columnDefinition = "text") private String contenidoCifrado;
    @Column(name = "creado_en", nullable = false) private Instant creadoEn;
    @Column(name = "vence_en", nullable = false) private Instant venceEn;
    @Column(name = "decidido_en") private Instant decididoEn;
    @Version @Column(nullable = false) private long version;
}
