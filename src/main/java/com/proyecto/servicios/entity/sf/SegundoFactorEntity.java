package com.proyecto.servicios.entity.sf;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "payment_second_factors")
@Getter @Setter @NoArgsConstructor
public class SegundoFactorEntity {
    @Id @Column(name = "usuario_id") private UUID usuarioId;
    @JsonIgnore @Column(name = "secreto_cifrado", columnDefinition = "TEXT") private String secretoCifrado;
    @Column(length = 64) private String huella;
    @Column(name = "ultimo_paso", nullable = false) private long ultimoPaso = -1;
    @JsonIgnore @Column(name = "pendiente_cifrado", columnDefinition = "TEXT") private String pendienteCifrado;
    @Column(name = "sesion_registro") private UUID sesionRegistro;
    @Column(name = "vence_en") private Instant venceEn;
    @Column(nullable = false) private int intentos;
    @Column(name = "bloqueado_hasta") private Instant bloqueadoHasta;
    @JsonIgnore @Column(name = "recuperacion_hashes", columnDefinition = "TEXT") private String recuperacionHashes;
    @Version @Column(nullable = false) private long version;
}
