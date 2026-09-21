package com.preonsurl.apis.link.entity;

import com.preonsurl.apis.link.enums.AccessPolicyMode;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "access_policies", indexes = {
        @Index(name = "idx_access_policies_short_url_id", columnList = "short_url_id"),
        @Index(name = "idx_access_policies_mode", columnList = "mode")
})
public class AccessPolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "short_url_id", nullable = false, unique = true)
    private Long shortUrlId;

    @Enumerated(EnumType.STRING)
    @Column(name = "mode", nullable = false, length = 32)
    private AccessPolicyMode mode = AccessPolicyMode.PUBLIC;

    @Column(name = "pin_hash", length = 255)
    private String pinHash;

    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    @Column(name = "ip_allowlist", columnDefinition = "TEXT")
    private String ipAllowlist;

    @Column(name = "countries", length = 512)
    private String countries;

    @Column(name = "device_types", length = 512)
    private String deviceTypes;

    @Column(name = "referrers", columnDefinition = "TEXT")
    private String referrers;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public AccessPolicy(Long shortUrlId, AccessPolicyMode mode) {
        this.shortUrlId = shortUrlId;
        this.mode = mode != null ? mode : AccessPolicyMode.PUBLIC;
    }

    public boolean isSecured() {
        return mode == AccessPolicyMode.SECURED;
    }

    public boolean hasPin() {
        return pinHash != null && !pinHash.isBlank();
    }

    public boolean hasPassword() {
        return passwordHash != null && !passwordHash.isBlank();
    }

    public boolean isPinOrPasswordProtected() {
        return isSecured() && (hasPin() || hasPassword());
    }

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
