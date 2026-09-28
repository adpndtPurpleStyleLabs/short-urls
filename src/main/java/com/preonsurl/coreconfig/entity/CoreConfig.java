package com.preonsurl.coreconfig.entity;

import com.preonsurl.coreconfig.enums.ConfigValueType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "core_config",
        indexes = {
                @Index(name = "idx_core_config_active", columnList = "active"),
                @Index(name = "idx_core_config_updated_at", columnList = "updated_at")
        },
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_core_config_key",
                        columnNames = "config_key"
                )
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CoreConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(
            name = "config_key",
            nullable = false,
            unique = true,
            length = 150
    )
    private String configKey;

    @Column(
            name = "config_value",
            nullable = false,
            columnDefinition = "TEXT"
    )
    private String configValue;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "value_type",
            nullable = false,
            length = 30
    )
    private ConfigValueType valueType;

    @Column(length = 500)
    private String description;

    @Column(nullable = false)
    private boolean active;

    @Column(nullable = false)
    private Long version;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (active == false) {
            active = true;
        }

        if (version == null) {
            version = 1L;
        }

        LocalDateTime now = LocalDateTime.now();

        if (createdAt == null) {
            createdAt = now;
        }

        if (updatedAt == null) {
            updatedAt = now;
        }
    }
}