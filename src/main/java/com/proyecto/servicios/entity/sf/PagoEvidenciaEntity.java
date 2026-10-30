package com.proyecto.servicios.entity.sf;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.Instant;
import java.util.UUID;

/** Append-only evidence; payloads are encrypted, never application log entries. */
@Entity @Table(name = "payment_evidence") @Getter @NoArgsConstructor
public class PagoEvidenciaEntity {
    @Id private UUID id;
    @Column(name = "pago_id", nullable = false) private UUID pagoId;
    @Column(nullable = false, length = 16) private String metodo;
    @Column(nullable = false, columnDefinition = "text") private String contenido;
    @Column(name = "creado_en", nullable = false) private Instant creadoEn;
    public PagoEvidenciaEntity(UUID pagoId, String metodo, String contenido) {
        this.id = UUID.randomUUID(); this.pagoId = pagoId; this.metodo = metodo;
        this.contenido = contenido; this.creadoEn = Instant.now();
    }
}
