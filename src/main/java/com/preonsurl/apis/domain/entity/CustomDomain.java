package com.preonsurl.apis.domain.entity;

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
        name = "custom_domains",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_custom_domains_domain",
                        columnNames = "domain"
                )
        },
        indexes = {
                @Index(name = "idx_custom_domains_user_id", columnList = "user_id"),
                @Index(name = "idx_custom_domains_status", columnList = "status")
        }
)
public class CustomDomain {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "domain", nullable = false, length = 255)
    private String domain;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private DomainStatus status = DomainStatus.VERIFICATION_REQUIRED;

    @Column(name = "cname_target", nullable = false, length = 255)
    private String cnameTarget;

    @Column(name = "verification_error", length = 1024)
    private String verificationError;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    public CustomDomain(Long userId, String domain, String cnameTarget) {
        this.userId = userId;
        this.domain = domain;
        this.cnameTarget = cnameTarget;
        this.status = DomainStatus.VERIFICATION_REQUIRED;
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
