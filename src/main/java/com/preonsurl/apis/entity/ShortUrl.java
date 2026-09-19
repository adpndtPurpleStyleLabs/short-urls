package com.preonsurl.apis.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "short_urls", indexes = {
        @Index(name = "idx_short_urls_user_id", columnList = "user_id"),
        @Index(name = "idx_short_urls_original_url", columnList = "original_url"),
        @Index(name = "idx_short_urls_dir_type_original", columnList = "dir_type, original_url"),
        @Index(name = "idx_short_urls_dir_code", columnList = "dir_type, short_code"),
        @Index(name = "idx_short_urls_expire_at", columnList = "expire_at")
})
public class ShortUrl {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "short_code", nullable = false, unique = true, length = 64)
    private String shortCode;

    @Column(name = "original_url", nullable = false, length = 2048)
    private String originalUrl;

    @Column(name = "dir_type", length = 128)
    private String dirType;

    @Column(name = "full_short_url", nullable = false, length = 512)
    private String fullShortUrl;

    @Column(name = "click_count", nullable = false)
    private long clickCount = 0;

    @Column(name = "expire_at", nullable = false)
    private Instant expireAt;

    @Column(name = "usage_limit")
    private Long usageLimit;

    @Column(name = "note", length = 1024)
    private String note;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public ShortUrl(
            String shortCode,
            String originalUrl,
            String dirType,
            String fullShortUrl,
            Instant expireAt
    ) {
        this(shortCode, originalUrl, dirType, fullShortUrl, expireAt, null);
    }

    public ShortUrl(
            String shortCode,
            String originalUrl,
            String dirType,
            String fullShortUrl
    ) {
        this(shortCode, originalUrl, dirType, fullShortUrl, Instant.now().plus(3650, java.time.temporal.ChronoUnit.DAYS), null);
    }

    public ShortUrl(
            String shortCode,
            String originalUrl,
            String dirType,
            String fullShortUrl,
            Instant expireAt,
            Long usageLimit
    ) {
        this.shortCode = shortCode;
        this.originalUrl = originalUrl;
        this.dirType = dirType;
        this.fullShortUrl = fullShortUrl;
        this.expireAt = expireAt;
        this.usageLimit = usageLimit;
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