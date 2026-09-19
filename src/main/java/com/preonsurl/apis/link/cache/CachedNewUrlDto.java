package com.preonsurl.apis.link.cache;

import lombok.Data;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

@Data
public class CachedNewUrlDto {

    private final Long id;
    private final String fullShortUrl;
    private final String originalUrl;
    private final Instant expireAt;
    private final Long usageLimit;
    private final AtomicLong clickCount;

    public CachedNewUrlDto(Long id, String fullShortUrl, String originalUrl, Instant expireAt, Long usageLimit, long initialClickCount) {
        this.id = id;
        this.fullShortUrl = fullShortUrl;
        this.originalUrl = originalUrl;
        this.expireAt = expireAt;
        this.usageLimit = usageLimit;
        this.clickCount = new AtomicLong(initialClickCount);
    }

    public long getClickCount() {
        return clickCount.get();
    }

    public long incrementClickCount() {
        return clickCount.incrementAndGet();
    }

    public boolean isExpired() {
        return expireAt != null && !Instant.now().isBefore(expireAt);
    }

    public boolean isUsageLimitBreached() {
        return usageLimit != null && clickCount.get() >= usageLimit;
    }
}