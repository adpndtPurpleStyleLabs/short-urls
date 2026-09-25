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
        name = "public_secure_urls",
        indexes = {
                @Index(name = "idx_psec_short_key", columnList = "short_key", unique = true),
                @Index(name = "idx_psec_created_at", columnList = "created_at"),
                @Index(name = "idx_psec_user_id", columnList = "user_id")
        }
)
public class PublicSecureUrl {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "short_key", nullable = false, unique = true, length = 64)
    private String shortKey;

    @Column(name = "original_url", nullable = false, columnDefinition = "TEXT")
    private String originalUrl;

    @Column(name = "psecure_url", nullable = false, length = 512)
    private String psecureUrl;

    @Column(name = "link_mode", length = 32)
    private String linkMode;

    @Column(name = "pin", length = 32)
    private String pin;

    @Column(name = "password", length = 255)
    private String password;

    @Column(name = "mode", length = 32)
    private String mode;

    @Column(name = "access_policies_json", columnDefinition = "TEXT")
    private String accessPoliciesJson;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Column(name = "user_agent", length = 512)
    private String userAgent;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "click_count", nullable = false)
    private long clickCount = 0L;

    @Column(name = "user_id")
    private Long userId;

    public PublicSecureUrl() {
    }

    public PublicSecureUrl(String shortKey, String originalUrl, String psecureUrl, String linkMode,
                           String pin, String password, String mode, String accessPoliciesJson,
                           String ipAddress, String userAgent, Instant createdAt, Long userId) {
        this.shortKey = shortKey;
        this.originalUrl = originalUrl;
        this.psecureUrl = psecureUrl;
        this.linkMode = linkMode != null ? linkMode : "REDIRECT";
        this.pin = pin;
        this.password = password;
        this.mode = mode != null ? mode : "SECURED";
        this.accessPoliciesJson = accessPoliciesJson;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.active = true;
        this.clickCount = 0L;
        this.userId = userId;
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

    public String getOriginalUrl() {
        return originalUrl;
    }

    public void setOriginalUrl(String originalUrl) {
        this.originalUrl = originalUrl;
    }

    public String getPsecureUrl() {
        return psecureUrl;
    }

    public void setPsecureUrl(String psecureUrl) {
        this.psecureUrl = psecureUrl;
    }

    public String getLinkMode() {
        return linkMode;
    }

    public void setLinkMode(String linkMode) {
        this.linkMode = linkMode;
    }

    public String getPin() {
        return pin;
    }

    public void setPin(String pin) {
        this.pin = pin;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getAccessPoliciesJson() {
        return accessPoliciesJson;
    }

    public void setAccessPoliciesJson(String accessPoliciesJson) {
        this.accessPoliciesJson = accessPoliciesJson;
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

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public long getClickCount() {
        return clickCount;
    }

    public void setClickCount(long clickCount) {
        this.clickCount = clickCount;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }
}
