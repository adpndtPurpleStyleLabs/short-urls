package com.preonsurl.apis.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "short_urls_access_log", indexes = {
        @Index(name = "idx_access_log_short_url_id", columnList = "short_url_id"),
        @Index(name = "idx_access_log_short_code", columnList = "short_code"),
        @Index(name = "idx_access_log_accessed_at", columnList = "accessed_at")
})
public class ShortUrlAccessLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "short_url_id", nullable = false)
    private Long shortUrlId;

    @Column(name = "short_code", nullable = false, length = 64)
    private String shortCode;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Column(name = "user_agent", length = 512)
    private String userAgent;

    @Column(name = "referer", length = 1024)
    private String referer;

    @Column(name = "accessed_at", nullable = false, updatable = false)
    private LocalDateTime accessedAt;

    public ShortUrlAccessLog() {
    }

    public ShortUrlAccessLog(Long shortUrlId, String shortCode, String ipAddress, String userAgent, String referer) {
        this.shortUrlId = shortUrlId;
        this.shortCode = shortCode;
        this.ipAddress = ipAddress;
        this.userAgent = truncate(userAgent, 512);
        this.referer = truncate(referer, 1024);
    }

    @PrePersist
    protected void onCreate() {
        if (this.accessedAt == null) {
            this.accessedAt = LocalDateTime.now();
        }
    }

    private static String truncate(String val, int maxLen) {
        if (val == null) return null;
        return val.length() > maxLen ? val.substring(0, maxLen) : val;
    }

    public Long getId() {
        return id;
    }

    public Long getShortUrlId() {
        return shortUrlId;
    }

    public void setShortUrlId(Long shortUrlId) {
        this.shortUrlId = shortUrlId;
    }

    public String getShortCode() {
        return shortCode;
    }

    public void setShortCode(String shortCode) {
        this.shortCode = shortCode;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = truncate(userAgent, 512);
    }

    public String getReferer() {
        return referer;
    }

    public void setReferer(String referer) {
        this.referer = truncate(referer, 1024);
    }

    public LocalDateTime getAccessedAt() {
        return accessedAt;
    }

    public void setAccessedAt(LocalDateTime accessedAt) {
        this.accessedAt = accessedAt;
    }
}
