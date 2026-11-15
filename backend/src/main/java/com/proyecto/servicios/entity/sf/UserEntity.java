package com.proyecto.servicios.entity.sf;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Version;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "app_users")
public class UserEntity {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 254)
    private String email;

    @Column(nullable = false, unique = true, length = 254)
    private String identifier;

    @Column(name = "password_hash", nullable = false, length = 60)
    private String passwordHash;

    @Column(name = "full_name", nullable = false, length = 203)
    private String fullName;

    @Column(nullable = false)
    private boolean enabled;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 9)
    private RolUsuario rol = RolUsuario.CLIENTE;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id", unique = true, updatable = false)
    private ClienteEntity cliente;

    protected UserEntity() {
    }

    public UserEntity(UUID id, String email, String identifier, String passwordHash,
                      String fullName, boolean enabled, Instant createdAt) {
        this.id = id;
        this.email = email;
        this.identifier = identifier;
        this.passwordHash = passwordHash;
        this.fullName = fullName;
        this.enabled = enabled;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    public UserEntity(UUID id, String email, String passwordHash, String fullName,
                      ClienteEntity cliente, Instant createdAt) {
        this(id, email, email, passwordHash, fullName, true, createdAt);
        this.cliente = java.util.Objects.requireNonNull(cliente, "El cliente es obligatorio");
    }

    public UUID getId() { return id; }
    public RolUsuario getRol() { return rol; }

    public static UserEntity crearEjecutivo(String correo, String hash, Instant ahora) {
        UserEntity usuario = new UserEntity(UUID.randomUUID(), correo, correo, hash,
                "Ejecutivo inicial", true, ahora);
        usuario.rol = RolUsuario.EJECUTIVO;
        return usuario;
    }
    public String getEmail() { return email; }
    public String getIdentifier() { return identifier; }
    public String getPasswordHash() { return passwordHash; }
    public String getFullName() { return fullName; }
    public boolean isEnabled() { return enabled; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public ClienteEntity getCliente() { return cliente; }

    public void actualizarPerfilCliente(String correo, String nombreCompleto) {
        this.email = correo;
        this.identifier = correo;
        this.fullName = nombreCompleto;
        this.updatedAt = Instant.now();
    }

    public void desactivar() {
        this.enabled = false;
        this.updatedAt = Instant.now();
    }

    public void cambiarContrasena(String hash, Instant fechaActualizacion) {
        this.passwordHash = java.util.Objects.requireNonNull(hash, "El hash es obligatorio");
        this.updatedAt = fechaActualizacion;
    }
}
