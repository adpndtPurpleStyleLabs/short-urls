package com.preonsurl.apis.publiclink.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(
        name = "public_secure_url_access_log",
        indexes = {
                @Index(name = "idx_psec_log_short_key", columnList = "short_key"),
                @Index(name = "idx_psec_log_url_id", columnList = "public_secure_url_id"),
                @Index(name = "idx_psec_log_accessed_at", columnList = "accessed_at")
        }
)
public class PublicSecureUrlAccessLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "short_key", nullable = false, length = 64)
    private String shortKey;

    @Column(name = "public_secure_url_id")
    private Long publicSecureUrlId;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Column(name = "user_agent", length = 512)
    private String userAgent;

    @Column(name = "referer", length = 512)
    private String referer;

    @Column(name = "accessed_at", nullable = false)
    private Instant accessedAt;

    @Column(name = "status", length = 32)
    private String status;

    public PublicSecureUrlAccessLog() {
    }

    public PublicSecureUrlAccessLog(String shortKey, Long publicSecureUrlId, String ipAddress,
                                   String userAgent, String referer, Instant accessedAt, String status) {
        this.shortKey = shortKey;
        this.publicSecureUrlId = publicSecureUrlId;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
        this.referer = referer;
        this.accessedAt = accessedAt != null ? accessedAt : Instant.now();
        this.status = status != null ? status : "REDIRECT";
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getShortKey() {
        return shortKey;
    }

    public void setShortKey(String shortKey) {
        this.shortKey = shortKey;
    }

    public Long getPublicSecureUrlId() {
        return publicSecureUrlId;
    }

    public void setPublicSecureUrlId(Long publicSecureUrlId) {
        this.publicSecureUrlId = publicSecureUrlId;
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
        this.userAgent = userAgent;
    }

    public String getReferer() {
        return referer;
    }

    public void setReferer(String referer) {
        this.referer = referer;
    }

    public Instant getAccessedAt() {
        return accessedAt;
    }

    public void setAccessedAt(Instant accessedAt) {
        this.accessedAt = accessedAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
