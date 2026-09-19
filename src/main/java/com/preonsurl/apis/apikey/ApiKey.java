package com.preonsurl.apis.apikey;

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
        name = "api_keys",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_api_keys_hash",
                        columnNames = "api_key_hash"
                )
        },
        indexes = {
                @Index(name = "idx_api_keys_user_active", columnList = "user_id, active"),
                @Index(name = "idx_api_keys_hash_active", columnList = "api_key_hash, active")
        }
)
public class ApiKey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "api_key_hash", nullable = false, length = 255)
    private String apiKeyHash;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}