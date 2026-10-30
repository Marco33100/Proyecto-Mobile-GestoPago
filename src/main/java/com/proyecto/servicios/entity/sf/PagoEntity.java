package com.proyecto.servicios.entity.sf;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "payment_orders")
@Getter @Setter @NoArgsConstructor
public class PagoEntity {
    public enum Estado { PREPARADO, ENVIADO, PENDIENTE, APLICADO, RECHAZADO, EXPIRADO, REVISION }
    @Id private UUID id;
    @Column(name = "usuario_id", nullable = false) private UUID usuarioId;
    @Column(name = "cuenta_id", nullable = false) private UUID cuentaId;
    @Column(name = "numero_cuenta", nullable = false, length = 24) private String numeroCuenta;
    @Column(nullable = false, length = 64) private String huella;
    @Column(name = "huella_operacion", length = 64) private String huellaOperacion;
    @Column(name = "huella_catalogo", length = 64) private String huellaCatalogo;
    @Column(name = "huella_reglas", length = 64) private String huellaReglas;
    @Column(name = "reglas_cifradas", columnDefinition = "text") private String reglasCifradas;
    @Column(name = "huella_clave", nullable = false, length = 64) private String huellaClave;
    @Column(nullable = false, unique = true, length = 10) private String upc;
    @Column(nullable = false, length = 250) private String concepto;
    @Column(nullable = false, precision = 19, scale = 2) private BigDecimal monto;
    @Column(name = "payload_cifrado", nullable = false, columnDefinition = "text") private String payloadCifrado;
    @Column(nullable = false, length = 1500) private String reto;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private Estado estado;
    @Column(name = "creado_en", nullable = false) private Instant creadoEn;
    @Column(name = "vence_en", nullable = false) private Instant venceEn;
    @Column(name = "enviado_en") private Instant enviadoEn;
    @Column(name = "proxima_confirmacion") private Instant proximaConfirmacion;
    @Column(name = "intento_confirmacion") private UUID intentoConfirmacion;
    @Column(name = "numero_confirmaciones", nullable = false) private int numeroConfirmaciones;
    @Column(name = "motivo_revision", length = 64) private String motivoRevision;
    @Column(name = "codigo_proveedor", length = 40) private String codigoProveedor;
    @Column(name = "duplicado_proveedor", nullable = false) private boolean duplicadoProveedor;
    @Column(name = "id_transaccion_previa", length = 60) private String idTransaccionPrevia;
    @Column(name = "autorizacion_previa", length = 60) private String autorizacionPrevia;
    @Column(name = "id_transaccion", length = 60) private String idTransaccion;
    @Column(length = 60) private String autorizacion;
    @Column(name = "recibo_cifrado", columnDefinition = "text") private String reciboCifrado;
    @Column(name = "referencia_validada", nullable = false) private boolean referenciaValidada;
    @Column(name = "consulta_referencia_cifrada", columnDefinition = "text") private String consultaReferenciaCifrada;
    @Column(name = "consulta_referencia_en") private Instant consultaReferenciaEn;
    @Column(name = "tipo_front") private Integer tipoFront;
    @Column(name = "requiere_pin", nullable = false) private boolean requierePin;
    @Version @Column(nullable = false) private long version;
}
