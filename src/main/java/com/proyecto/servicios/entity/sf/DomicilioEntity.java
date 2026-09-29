package com.proyecto.servicios.entity.sf;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "domicilios")
public class DomicilioEntity {

    @Id
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cliente_id", nullable = false, unique = true)
    private ClienteEntity cliente;

    @Column(nullable = false, length = 100)
    private String calle;

    @Column(name = "numero_exterior", nullable = false, length = 20)
    private String numeroExterior;

    @Column(name = "numero_interior", length = 20)
    private String numeroInterior;

    @Column(nullable = false, length = 100)
    private String colonia;

    @Column(nullable = false, length = 100)
    private String municipio;

    @Column(nullable = false, length = 100)
    private String estado;

    @Column(name = "codigo_postal", nullable = false, length = 5)
    private String codigoPostal;

    @Column(nullable = false, length = 60)
    private String pais;

    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    private Instant fechaCreacion;

    @Column(name = "fecha_actualizacion", nullable = false)
    private Instant fechaActualizacion;

    @Version
    @Column(nullable = false)
    private long version;

    protected DomicilioEntity() {
    }

    public DomicilioEntity(
            UUID id,
            ClienteEntity cliente,
            String calle,
            String numeroExterior,
            String numeroInterior,
            String colonia,
            String municipio,
            String estado,
            String codigoPostal,
            String pais,
            Instant fechaCreacion
    ) {
        this.id = id;
        this.cliente = cliente;
        this.calle = calle;
        this.numeroExterior = numeroExterior;
        this.numeroInterior = numeroInterior;
        this.colonia = colonia;
        this.municipio = municipio;
        this.estado = estado;
        this.codigoPostal = codigoPostal;
        this.pais = pais;
        this.fechaCreacion = fechaCreacion;
        this.fechaActualizacion = fechaCreacion;
    }

    public UUID getId() { return id; }
    public ClienteEntity getCliente() { return cliente; }
    public String getCalle() { return calle; }
    public String getNumeroExterior() { return numeroExterior; }
    public String getNumeroInterior() { return numeroInterior; }
    public String getColonia() { return colonia; }
    public String getMunicipio() { return municipio; }
    public String getEstado() { return estado; }
    public String getCodigoPostal() { return codigoPostal; }
    public String getPais() { return pais; }
    public Instant getFechaCreacion() { return fechaCreacion; }
    public Instant getFechaActualizacion() { return fechaActualizacion; }
    public long getVersion() { return version; }
}
