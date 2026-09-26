package com.preonsurl.apis.auth.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(
        name = "users",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_users_tenant_username",
                        columnNames = {"tenant_id", "username"}
                )
        },
        indexes = {
                @Index(
                        name = "idx_users_tenant_id",
                        columnList = "tenant_id"
                )
        }
)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    @Column(name = "username", nullable = false, length = 20)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "email", length = 255)
    private String email;

    @Column(name = "is_verified", nullable = false)
    private boolean isVerified = true;

    @Column(name = "verification_code", length = 6)
    private String verificationCode;

    @Column(name = "verification_code_expires_at")
    private Instant verificationCodeExpiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "plan", length = 20, nullable = false)
    private String plan = "FREE";

    @Column(name = "plan_updated_at")
    private Instant planUpdatedAt;

    public boolean isPro() {
        return "PRO".equalsIgnoreCase(this.plan);
    }

    public String getPlan() {
        return plan != null ? plan : "FREE";
    }

    public User(
            Long tenantId,
            String fullName,
            String username,
            String passwordHash
    ) {
        this.tenantId = tenantId;
        this.fullName = fullName;
        this.username = username;
        this.passwordHash = passwordHash;
        this.isVerified = true;
    }

    public User(
            Long tenantId,
            String fullName,
            String username,
            String passwordHash,
            String email,
            boolean isVerified,
            String verificationCode,
            Instant verificationCodeExpiresAt
    ) {
        this.tenantId = tenantId;
        this.fullName = fullName;
        this.username = username;
        this.passwordHash = passwordHash;
        this.email = email;
        this.isVerified = isVerified;
        this.verificationCode = verificationCode;
        this.verificationCodeExpiresAt = verificationCodeExpiresAt;
    }

    public boolean isVerificationCodeValid(String code) {
        if (code == null || verificationCode == null || verificationCodeExpiresAt == null) {
            return false;
        }
        return verificationCode.equals(code.trim()) && verificationCodeExpiresAt.isAfter(Instant.now());
    }

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
        if (plan == null) {
            plan = "FREE";
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}