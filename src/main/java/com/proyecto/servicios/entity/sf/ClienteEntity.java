package com.proyecto.servicios.entity.sf;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "clientes")
public class ClienteEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(updatable = false)
    private Long id;

    @Column(nullable = false, length = 50)
    private String nombre;

    @Column(name = "segundo_nombre", length = 50)
    private String segundoNombre;

    @Column(name = "apellido_paterno", nullable = false, length = 50)
    private String apellidoPaterno;

    @Column(name = "apellido_materno", nullable = false, length = 50)
    private String apellidoMaterno;

    @Column(name = "fecha_nacimiento", nullable = false)
    private LocalDate fechaNacimiento;

    @Column(nullable = false, unique = true, length = 18)
    private String curp;

    @Column(nullable = false, unique = true, length = 13)
    private String rfc;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Sexo sexo;

    @Column(nullable = false, length = 60)
    private String nacionalidad;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_civil", nullable = false, length = 11)
    private EstadoCivil estadoCivil;

    @Column(name = "referencia_reconocimiento_facial", length = 255)
    private String referenciaReconocimientoFacial;

    @Column(nullable = false, unique = true, length = 100)
    private String correo;

    @Column(name = "telefono_movil", nullable = false, length = 10)
    private String telefonoMovil;

    @Column(name = "telefono_alternativo", length = 10)
    private String telefonoAlternativo;

    @Column(nullable = false, length = 100)
    private String ocupacion;

    @Column(nullable = false, length = 150)
    private String empresa;

    @Column(name = "ingreso_mensual", nullable = false, precision = 19, scale = 2)
    private BigDecimal ingresoMensual;

    @Column(nullable = false)
    private boolean activo;

    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    private Instant fechaCreacion;

    @Column(name = "fecha_actualizacion", nullable = false)
    private Instant fechaActualizacion;

    @Version
    @Column(nullable = false)
    private long version;

    protected ClienteEntity() {
    }

    public ClienteEntity(
            String nombre,
            String segundoNombre,
            String apellidoPaterno,
            String apellidoMaterno,
            LocalDate fechaNacimiento,
            String curp,
            String rfc,
            Sexo sexo,
            String nacionalidad,
            EstadoCivil estadoCivil,
            String referenciaReconocimientoFacial,
            String correo,
            String telefonoMovil,
            String telefonoAlternativo,
            String ocupacion,
            String empresa,
            BigDecimal ingresoMensual,
            Instant fechaCreacion
    ) {
        this.nombre = nombre;
        this.segundoNombre = segundoNombre;
        this.apellidoPaterno = apellidoPaterno;
        this.apellidoMaterno = apellidoMaterno;
        this.fechaNacimiento = fechaNacimiento;
        this.curp = curp;
        this.rfc = rfc;
        this.sexo = sexo;
        this.nacionalidad = nacionalidad;
        this.estadoCivil = estadoCivil;
        this.referenciaReconocimientoFacial = referenciaReconocimientoFacial;
        this.correo = correo;
        this.telefonoMovil = telefonoMovil;
        this.telefonoAlternativo = telefonoAlternativo;
        this.ocupacion = ocupacion;
        this.empresa = empresa;
        this.ingresoMensual = ingresoMensual;
        this.activo = true;
        this.fechaCreacion = fechaCreacion;
        this.fechaActualizacion = fechaCreacion;
    }

    public void actualizar(
            String nombre,
            String segundoNombre,
            String apellidoPaterno,
            String apellidoMaterno,
            LocalDate fechaNacimiento,
            Sexo sexo,
            String nacionalidad,
            EstadoCivil estadoCivil,
            String referenciaReconocimientoFacial,
            String correo,
            String telefonoMovil,
            String telefonoAlternativo,
            String ocupacion,
            String empresa,
            BigDecimal ingresoMensual,
            Instant fechaActualizacion
    ) {
        this.nombre = nombre;
        this.segundoNombre = segundoNombre;
        this.apellidoPaterno = apellidoPaterno;
        this.apellidoMaterno = apellidoMaterno;
        this.fechaNacimiento = fechaNacimiento;
        this.sexo = sexo;
        this.nacionalidad = nacionalidad;
        this.estadoCivil = estadoCivil;
        this.referenciaReconocimientoFacial = referenciaReconocimientoFacial;
        this.correo = correo;
        this.telefonoMovil = telefonoMovil;
        this.telefonoAlternativo = telefonoAlternativo;
        this.ocupacion = ocupacion;
        this.empresa = empresa;
        this.ingresoMensual = ingresoMensual;
        this.fechaActualizacion = fechaActualizacion;
    }

    public void desactivar(Instant fechaActualizacion) {
        this.activo = false;
        this.fechaActualizacion = fechaActualizacion;
    }

    public Long getId() { return id; }
    public String getNombreCompleto() {
        return java.util.stream.Stream.of(nombre, segundoNombre, apellidoPaterno, apellidoMaterno)
                .filter(parte -> parte != null && !parte.isBlank())
                .collect(java.util.stream.Collectors.joining(" "));
    }
    public String getNombre() { return nombre; }
    public String getSegundoNombre() { return segundoNombre; }
    public String getApellidoPaterno() { return apellidoPaterno; }
    public String getApellidoMaterno() { return apellidoMaterno; }
    public LocalDate getFechaNacimiento() { return fechaNacimiento; }
    public String getCurp() { return curp; }
    public String getRfc() { return rfc; }
    public Sexo getSexo() { return sexo; }
    public String getNacionalidad() { return nacionalidad; }
    public EstadoCivil getEstadoCivil() { return estadoCivil; }
    public String getReferenciaReconocimientoFacial() { return referenciaReconocimientoFacial; }
    public String getCorreo() { return correo; }
    public String getTelefonoMovil() { return telefonoMovil; }
    public String getTelefonoAlternativo() { return telefonoAlternativo; }
    public String getOcupacion() { return ocupacion; }
    public String getEmpresa() { return empresa; }
    public BigDecimal getIngresoMensual() { return ingresoMensual; }
    public boolean isActivo() { return activo; }
    public Instant getFechaCreacion() { return fechaCreacion; }
    public Instant getFechaActualizacion() { return fechaActualizacion; }
    public long getVersion() { return version; }
}
