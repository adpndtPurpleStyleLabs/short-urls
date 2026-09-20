package com.preonsurl.apis.link.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "new_url_changes_log", indexes = {
        @Index(name = "idx_changes_log_url_id", columnList = "url_id"),
        @Index(name = "idx_changes_log_user_id", columnList = "user_id"),
        @Index(name = "idx_changes_log_action", columnList = "action"),
        @Index(name = "idx_changes_log_created_at", columnList = "created_at")
})
public class NewUrlChangeLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "url_id", nullable = false)
    private Long urlId;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "action", nullable = false, length = 32)
    private String action;

    @Column(name = "field_name", length = 64)
    private String fieldName;

    @Column(name = "old_value", length = 4096)
    private String oldValue;

    @Column(name = "new_value", length = 4096)
    private String newValue;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public NewUrlChangeLog(Long urlId, Long userId, String action, String fieldName, String oldValue, String newValue) {
        this.urlId = urlId;
        this.userId = userId;
        this.action = action;
        this.fieldName = fieldName;
        this.oldValue = oldValue;
        this.newValue = newValue;
    }

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
    }
}
