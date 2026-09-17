package com.preonsurl.apis.cache;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

public class CachedShortUrl {
    private final Long id;
    private final String shortCode;
    private final String dirType;
    private final String originalUrl;
    private final Instant expireAt;
    private final Long usageLimit;
    private final AtomicLong clickCount;

    public CachedShortUrl(Long id,
                          String shortCode,
                          String dirType,
                          String originalUrl,
                          Instant expireAt,
                          Long usageLimit,
                          long initialClickCount) {
        this.id = id;
        this.shortCode = shortCode;
        this.dirType = dirType;
        this.originalUrl = originalUrl;
        this.expireAt = expireAt;
        this.usageLimit = usageLimit;
        this.clickCount = new AtomicLong(initialClickCount);
    }

    public Long getId() {
        return id;
    }

    public String getShortCode() {
        return shortCode;
    }

    public String getDirType() {
        return dirType;
    }

    public String getOriginalUrl() {
        return originalUrl;
    }

    public Instant getExpireAt() {
        return expireAt;
    }

    public Long getUsageLimit() {
        return usageLimit;
    }

    public long getClickCount() {
        return clickCount.get();
    }

    public long incrementClickCount() {
        return clickCount.incrementAndGet();
    }

    public boolean isExpired() {
        return expireAt != null && Instant.now().isAfter(expireAt);
    }

    public boolean isUsageLimitBreached() {
        return usageLimit != null && clickCount.get() >= usageLimit;
    }
}
