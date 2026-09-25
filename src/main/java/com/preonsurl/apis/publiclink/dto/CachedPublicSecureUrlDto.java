package com.preonsurl.apis.publiclink.dto;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

public class CachedPublicSecureUrlDto {

    private volatile Long id;
    private final String shortKey;
    private final String originalUrl;
    private final String psecureUrl;
    private final String linkMode;
    private final String pin;
    private final String password;
    private final String mode;
    private final String accessPoliciesJson;
    private final boolean active;
    private final Instant createdAt;
    private final AtomicLong clickCount;

    public CachedPublicSecureUrlDto(Long id, String shortKey, String originalUrl, String psecureUrl,
                                   String linkMode, String pin, String password, String mode,
                                   String accessPoliciesJson, boolean active, Instant createdAt, long initialClickCount) {
        this.id = id;
        this.shortKey = shortKey;
        this.originalUrl = originalUrl;
        this.psecureUrl = psecureUrl;
        this.linkMode = linkMode != null ? linkMode : "REDIRECT";
        this.pin = pin;
        this.password = password;
        this.mode = mode != null ? mode : "SECURED";
        this.accessPoliciesJson = accessPoliciesJson;
        this.active = active;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.clickCount = new AtomicLong(initialClickCount);
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

    public String getOriginalUrl() {
        return originalUrl;
    }

    public String getPsecureUrl() {
        return psecureUrl;
    }

    public String getLinkMode() {
        return linkMode;
    }

    public String getPin() {
        return pin;
    }

    public String getPassword() {
        return password;
    }

    public String getMode() {
        return mode;
    }

    public String getAccessPoliciesJson() {
        return accessPoliciesJson;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public long getClickCount() {
        return clickCount.get();
    }

    public long incrementClickCount() {
        return clickCount.incrementAndGet();
    }

    public boolean hasPin() {
        return pin != null && !pin.isBlank();
    }

    public boolean hasPassword() {
        return password != null && !password.isBlank();
    }
}
