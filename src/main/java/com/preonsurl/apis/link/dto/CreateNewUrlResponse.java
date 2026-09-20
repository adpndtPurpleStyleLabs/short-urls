package com.preonsurl.apis.link.dto;

import com.preonsurl.apis.link.enums.LinkMode;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

@Schema(description = "Response payload after creating or resolving a short URL")
public record CreateNewUrlResponse(
        @Schema(description = "Full short URL for redirection", example = "http://localhost:8081/invoice/diwali-sale")
        String newUrl,

        @Schema(description = "Original destination URL", example = "https://example.com/products/item1")
        String originalUrl,

        @Schema(description = "Custom path if specified", example = "invoice/diwali-sale")
        String customPath,

        @Schema(description = "True if an identical short URL already existed and was returned without generating a new code", example = "false")
        boolean existing,

        @Schema(description = "Expiration timestamp in UTC ISO-8601 format", example = "2036-09-18T13:00:00Z")
        Instant expireAt,

        @Schema(description = "Configured usage limit or null if unlimited", example = "5")
        Long usageLimit,

        @Schema(description = "Optional note associated with the short URL", example = "Diwali campaign landing page")
        String notes,

        @Schema(description = "Tags associated with the short URL", example = "[\"marketing\", \"diwali\"]")
        List<String> tags,

        @Schema(description = "How the short URL delivers the destination", example = "REDIRECT")
        LinkMode linkMode,

        @Schema(description = "Whether the URL is active", example = "true")
        Boolean isActive
) {
    public CreateNewUrlResponse(
            String newUrl,
            String originalUrl,
            String customPath,
            boolean existing,
            Instant expireAt,
            Long usageLimit,
            String notes,
            List<String> tags,
            LinkMode linkMode
    ) {
        this(newUrl, originalUrl, customPath, existing, expireAt, usageLimit, notes, tags, linkMode, true);
    }

    public CreateNewUrlResponse(
            String newUrl,
            String originalUrl,
            String customPath,
            boolean existing,
            Instant expireAt,
            Long usageLimit,
            String notes,
            List<String> tags
    ) {
        this(newUrl, originalUrl, customPath, existing, expireAt, usageLimit, notes, tags, LinkMode.REDIRECT, true);
    }

    public CreateNewUrlResponse(
            String shortUrl,
            String originalUrl,
            boolean existing,
            Instant expireAt,
            Long usageLimit
    ) {
        this(shortUrl, originalUrl, null, existing, expireAt, usageLimit, null, null, LinkMode.REDIRECT);
    }

    public CreateNewUrlResponse(
            String shortUrl,
            String originalUrl,
            boolean existing,
            Instant expireAt
    ) {
        this(shortUrl, originalUrl, null, existing, expireAt, null, null, null, LinkMode.REDIRECT);
    }

    public Instant expiresAt() {
        return expireAt;
    }
}