package com.preonsurl.apis.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Response payload after creating or resolving a short URL")
public record CreateShortUrlResponse(
        @Schema(description = "Full short URL for redirection", example = "http://localhost:8081/invoice/2lk5j7e5rlo")
        String shortUrl,

        @Schema(description = "Generated or existing unique short code", example = "2lk5j7e5rlo")
        String shortCode,

        @Schema(description = "Original destination URL", example = "https://example.com/products/item1")
        String originalUrl,

        @Schema(description = "Directory prefix if specified", example = "invoice")
        String dirType,

        @Schema(description = "True if an identical short URL already existed and was returned without generating a new code", example = "false")
        boolean existing,

        @Schema(description = "Expiration timestamp in UTC ISO-8601 format", example = "2036-09-18T13:00:00Z")
        Instant expireAt,

        @Schema(description = "Configured usage limit or null if unlimited", example = "5")
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