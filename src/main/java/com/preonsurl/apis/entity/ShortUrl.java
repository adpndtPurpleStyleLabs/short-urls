package com.preonsurl.apis.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "short_urls", indexes = {
        @Index(name = "idx_short_urls_original_url", columnList = "original_url"),
        @Index(name = "idx_short_urls_dir_type_original", columnList = "dir_type, original_url"),
        @Index(name = "idx_short_urls_dir_code", columnList = "dir_type, short_code")
})
public class ShortUrl {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

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

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public ShortUrl() {
    }

    public ShortUrl(String shortCode, String originalUrl, String dirType, String fullShortUrl) {
        this.shortCode = shortCode;
        this.originalUrl = originalUrl;
        this.dirType = dirType;
        this.fullShortUrl = fullShortUrl;
    }

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getShortCode() {
        return shortCode;
    }

    public void setShortCode(String shortCode) {
        this.shortCode = shortCode;
    }

    public String getOriginalUrl() {
        return originalUrl;
    }

    public void setOriginalUrl(String originalUrl) {
        this.originalUrl = originalUrl;
    }

    public String getDirType() {
        return dirType;
    }

    public void setDirType(String dirType) {
        this.dirType = dirType;
    }

    public String getFullShortUrl() {
        return fullShortUrl;
    }

    public void setFullShortUrl(String fullShortUrl) {
        this.fullShortUrl = fullShortUrl;
    }

    public long getClickCount() {
        return clickCount;
    }

    public void setClickCount(long clickCount) {
        this.clickCount = clickCount;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
