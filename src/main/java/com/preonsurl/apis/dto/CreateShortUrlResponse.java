package com.preonsurl.apis.dto;

import java.time.Instant;

public record CreateShortUrlResponse(
        String shortUrl,
        String shortCode,
        String originalUrl,
        String dirType,
        boolean existing,
        Instant expireAt,
        Long usageLimit
) {
    public CreateShortUrlResponse(
            String shortUrl,
            String shortCode,
            String originalUrl,
            String dirType,
            boolean existing,
            Instant expireAt
    ) {
        this(shortUrl, shortCode, originalUrl, dirType, existing, expireAt, null);
    }

    public Instant expiresAt() {
        return expireAt;
    }
}