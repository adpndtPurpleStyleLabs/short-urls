package com.preonsurl.apis.link.entity;

import com.preonsurl.apis.link.enums.LinkMode;
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
        @Index(name = "idx_short_urls_custom_path", columnList = "custom_path"),
        @Index(name = "idx_short_urls_new_url", columnList = "new_url"),
        @Index(name = "idx_short_urls_link_mode", columnList = "link_mode"),
        @Index(name = "idx_short_urls_is_active", columnList = "is_active"),
        @Index(name = "idx_short_urls_expire_at", columnList = "expire_at")
})
public class NewUrl {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "short_code", nullable = false, unique = true, length = 64)
    private String shortCode;

    @Column(name = "original_url", nullable = false, length = 2048)
    private String originalUrl;

    @Column(name = "custom_path", length = 256)
    private String customPath;

    @Column(name = "new_url", nullable = false, length = 512)
    private String newUrl;

    @Column(name = "click_count", nullable = false)
    private long clickCount = 0;

    @Column(name = "expire_at", nullable = false)
    private Instant expireAt;

    @Column(name = "usage_limit")
    private Long usageLimit;

    @Enumerated(EnumType.STRING)
    @Column(name = "link_mode", nullable = false, length = 20)
    private LinkMode linkMode = LinkMode.REDIRECT;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    @Column(name = "note", length = 1024)
    private String note;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public NewUrl(
            String shortCode,
            String originalUrl,
            String customPath,
            String newUrl,
            Instant expireAt,
            Long usageLimit,
            LinkMode linkMode
    ) {
        this.shortCode = shortCode;
        this.originalUrl = originalUrl;
        this.customPath = customPath;
        this.newUrl = newUrl;
        this.expireAt = expireAt;
        this.usageLimit = usageLimit;
        this.linkMode = linkMode != null ? linkMode : LinkMode.REDIRECT;
    }

    public NewUrl(
            String shortCode,
            String originalUrl,
            String newUrl
    ) {
        this(shortCode, originalUrl, null, newUrl, Instant.now().plus(3650, java.time.temporal.ChronoUnit.DAYS), null, LinkMode.REDIRECT);
    }

    public NewUrl(
            String shortCode,
            String originalUrl,
            String newUrl,
            Instant expireAt
    ) {
        this(shortCode, originalUrl, null, newUrl, expireAt, null, LinkMode.REDIRECT);
    }

    public NewUrl(
            String shortCode,
            String originalUrl,
            String newUrl,
            Instant expireAt,
            Long usageLimit
    ) {
        this(shortCode, originalUrl, null, newUrl, expireAt, usageLimit, LinkMode.REDIRECT);
    }

    public NewUrl(
            String shortCode,
            String originalUrl,
            String newUrl,
            Instant expireAt,
            Long usageLimit,
            LinkMode linkMode
    ) {
        this(shortCode, originalUrl, null, newUrl, expireAt, usageLimit, linkMode);
    }

    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean active) {
        this.isActive = active;
    }

    public void setIsActive(boolean isActive) {
        this.isActive = isActive;
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