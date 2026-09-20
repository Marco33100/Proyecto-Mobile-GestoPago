package com.proyecto.servicios.entity.sf;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_sessions")
public class UserSessionEntity {

    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "session_id", nullable = false, unique = true)
    private UUID sessionId;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UserSessionEntity() {
    }

    public UserSessionEntity(UUID userId, UUID sessionId, Instant expiresAt, Instant updatedAt) {
        this.userId = userId;
        this.sessionId = sessionId;
        this.expiresAt = expiresAt;
        this.updatedAt = updatedAt;
    }

    public UUID getUserId() { return userId; }
    public UUID getSessionId() { return sessionId; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
