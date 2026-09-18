package com.preonsurl.apis.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;

import java.time.Instant;

public record CreateShortUrlRequest(
        @NotBlank(message = "url cannot be empty")
        String url,
        String dirType,
        ExpireRequest expire,
        Object usageLimit
) {
    public CreateShortUrlRequest(String url, String dirType, ExpireRequest expire) {
        this(url, dirType, expire, null);
    }

    public Long resolvedUsageLimit() {
        if (usageLimit == null) {
            return null;
        }
        if (usageLimit instanceof Number number) {
            long val = number.longValue();
            if (val <= 0) {
                throw new IllegalArgumentException("usageLimit must be a positive integer or 'once' or 'unlimited'");
            }
            return val;
        }
        if (usageLimit instanceof String str) {
            String trimmed = str.trim().toLowerCase();
            if (trimmed.isEmpty() || "unlimited".equals(trimmed)) {
                return null;
            }
            if ("once".equals(trimmed)) {
                return 1L;
            }
            try {
                long val = Long.parseLong(trimmed);
                if (val <= 0) {
                    throw new IllegalArgumentException("usageLimit must be a positive integer");
                }
                return val;
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid usageLimit: '" + str + "'. Expected 'unlimited', 'once', or a positive integer");
            }
        }
        throw new IllegalArgumentException("Invalid usageLimit format: " + usageLimit);
    }

    public record ExpireRequest(
            boolean enabled,
            Instant expireAt
    ) {
    }
}
