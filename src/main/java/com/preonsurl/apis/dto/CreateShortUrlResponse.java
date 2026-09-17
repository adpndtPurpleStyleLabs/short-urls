package com.preonsurl.apis.dto;

import java.time.Instant;

public record CreateShortUrlResponse(
        String shortUrl,
        String shortCode,
        String originalUrl,
        String dirType,
        boolean existing,
        Instant expireAt
) {
    public Instant expiresAt() {
        return expireAt;
    }
}