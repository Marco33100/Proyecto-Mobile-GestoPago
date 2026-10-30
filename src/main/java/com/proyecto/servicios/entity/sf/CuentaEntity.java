package com.proyecto.servicios.entity.sf;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "cuentas")
public class CuentaEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cliente_id", nullable = false)
    private ClienteEntity cliente;

    @Column(name = "numero_cuenta", nullable = false, unique = true, length = 24)
    private String numeroCuenta;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal saldo;

    @Column(name = "saldo_retenido", nullable = false, precision = 19, scale = 2)
    private BigDecimal saldoRetenido = BigDecimal.ZERO;

    @Column(nullable = false)
    private boolean activa;

    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    private Instant fechaCreacion;

    @Column(name = "fecha_actualizacion", nullable = false)
    private Instant fechaActualizacion;

    @Version
    @Column(nullable = false)
    private long version;

    protected CuentaEntity() {
    }

    public CuentaEntity(
            UUID id,
            ClienteEntity cliente,
            String numeroCuenta,
            BigDecimal saldoInicial,
            Instant fechaCreacion
    ) {
        this.id = id;
        this.cliente = cliente;
        this.numeroCuenta = numeroCuenta;
        this.saldo = saldoInicial;
        this.activa = true;
        this.fechaCreacion = fechaCreacion;
        this.fechaActualizacion = fechaCreacion;
    }

    public void desactivar(Instant ahora) {
        if (activa) {
            this.activa = false;
            this.fechaActualizacion = ahora;
        }
    }

    public UUID getId() { return id; }
    public ClienteEntity getCliente() { return cliente; }
    public String getNumeroCuenta() { return numeroCuenta; }
    public BigDecimal getSaldo() { return saldo; }
    public BigDecimal getSaldoRetenido() { return saldoRetenido; }
    public BigDecimal getSaldoDisponible() { return saldo.subtract(saldoRetenido); }

    public void retener(BigDecimal importe, Instant ahora) {
        if (!activa || importe == null || importe.signum() <= 0 || getSaldoDisponible().compareTo(importe) < 0) {
            throw new IllegalStateException("Cuenta sin disponibilidad para la retencion");
        }
        saldoRetenido = saldoRetenido.add(importe);
        fechaActualizacion = ahora;
    }

    public void resolverRetencion(BigDecimal importe, boolean aplicar, Instant ahora) {
        if (importe == null || importe.signum() <= 0 || saldoRetenido.compareTo(importe) < 0) {
            throw new IllegalStateException("Retencion inconsistente");
        }
        saldoRetenido = saldoRetenido.subtract(importe);
        if (aplicar) saldo = saldo.subtract(importe);
        fechaActualizacion = ahora;
    }
    public boolean isActiva() { return activa; }
    public Instant getFechaCreacion() { return fechaCreacion; }
    public Instant getFechaActualizacion() { return fechaActualizacion; }
    public long getVersion() { return version; }
}
