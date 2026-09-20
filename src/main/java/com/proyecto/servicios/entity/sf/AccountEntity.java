package com.proyecto.servicios.entity.sf;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "accounts")
public class AccountEntity {

    @Id
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private UserEntity user;

    @Column(name = "account_number", nullable = false, unique = true, length = 50)
    private String accountNumber;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AccountEntity() {
    }

    public AccountEntity(UUID id, UserEntity user, String accountNumber, String status, Instant createdAt) {
        this.id = id;
        this.user = user;
        this.accountNumber = accountNumber;
        this.status = status;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public UserEntity getUser() { return user; }
    public String getAccountNumber() { return accountNumber; }
    public String getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
}
