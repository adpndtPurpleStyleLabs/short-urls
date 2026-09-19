package com.preonsurl.apis.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.util.regex.Pattern;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Request payload for creating a shortened URL")
public record CreateShortUrlRequest(
        @NotBlank(message = "url cannot be empty")
        @Schema(description = "Original target URL to shorten (must start with http:// or https://)", example = "https://example.com/products/item1", requiredMode = Schema.RequiredMode.REQUIRED)
        String url,

        @Schema(description = "Optional directory prefix for grouping short URLs (e.g., 'deals', 'invoice')", example = "invoice", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String dirType,

        @Schema(description = "Optional custom slug configuration", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        SlugRequest slug,

        @Schema(description = "Optional expiration policy", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        ExpireRequest expire,

        @Schema(description = "Usage limit: 'once', 'unlimited', a positive integer, or null for unlimited", example = "5", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        Object usageLimit
) {
    private static final Pattern SLUG_PATTERN =
            Pattern.compile(
                    "^[a-zA-Z0-9_-]+(?:/[a-zA-Z0-9_-]+)*$"
            );

    public CreateShortUrlRequest(String url, String dirType, ExpireRequest expire) {
        this(url, dirType, null, expire, null);
    }

    public String resolvedSlug() {
        if (slug == null) {
            return null;
        }
        String val = slug.value();
        if (val == null || val.isBlank()) {
            throw new IllegalArgumentException("Slug cannot be empty");
        }
        String trimmed = val.trim();
        if (!SLUG_PATTERN.matcher(trimmed).matches()) {
            throw new IllegalArgumentException("Invalid slug format: '" + val + "'. Must match pattern: " + SLUG_PATTERN.pattern());
        }
        return trimmed;
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

    public record SlugRequest(

            @Schema(
                    description = "Custom path/slug for the shortened URL. " +
                            "Supports simple slugs or hierarchical paths.",
                    example = "diwali-sale",
                    requiredMode = Schema.RequiredMode.REQUIRED
            )
            String value

    ) {
    }



    public record ExpireRequest(
            boolean enabled,
            Instant expireAt
    ) {
    }
}
