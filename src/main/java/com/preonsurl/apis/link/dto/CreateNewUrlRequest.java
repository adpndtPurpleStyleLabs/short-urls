package com.preonsurl.apis.link.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.preonsurl.apis.link.enums.LinkMode;
import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.util.List;
import java.util.regex.Pattern;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Request payload for creating a shortened URL")
public record CreateNewUrlRequest(
        @NotBlank(message = "url cannot be empty")
        @Schema(description = "Original target URL to shorten (must start with http:// or https://)", example = "https://example.com/products/item1", requiredMode = Schema.RequiredMode.REQUIRED)
        String url,

        @Schema(description = "Optional custom path for the shortened URL (e.g., 'diwali-sale', 'invoice/diwali-sale')", example = "invoice/diwali-sale", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        @JsonAlias({"slug", "path"})
        String customPath,

        @Schema(description = "Optional expiration timestamp in UTC ISO-8601 format. Must be in the future.", example = "2026-12-31T23:59:59Z", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        @JsonAlias({"expire", "expiresAt", "expire_at"})
        Instant expireAt,

        @Schema(description = "Usage limit: 'once', 'unlimited', a positive integer, or null for unlimited", example = "5", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        Object usageLimit,

        @Schema(description = "Optional note for this shortened URL", example = "Diwali campaign landing page", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        @JsonAlias({"note", "notes"})
        String notes,

        @Schema(description = "Optional tags for organizing and filtering URLs", example = "[\"marketing\", \"diwali\", \"campaign\"]", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        List<String> tags,

        @Schema(
                description = "How SecureURL delivers the destination. REDIRECT navigates the browser to the destination, IFRAME embeds it, PROXY serves it through SecureURL, and MIRROR serves a rewritten copy.",
                example = "REDIRECT",
                defaultValue = "REDIRECT",
                requiredMode = Schema.RequiredMode.NOT_REQUIRED
        )
        LinkMode linkMode,

        @Schema(
                description = "Whether to add/generate a short code for this URL.",
                example = "true",
                defaultValue = "false",
                requiredMode = Schema.RequiredMode.NOT_REQUIRED
        )
        @JsonAlias({"AddShortCode", "add_short_code"})
        Boolean addShortCode

) {
    private static final Pattern CUSTOM_PATH_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]+(?:/[a-zA-Z0-9_-]+)*$");

    public CreateNewUrlRequest(String url, String customPath, Instant expireAt) {
        this(url, customPath, expireAt, null, null, null, LinkMode.REDIRECT, false);
    }

    public boolean resolvedAddShortCode() {
        return Boolean.TRUE.equals(addShortCode);
    }

    public LinkMode resolvedLinkMode() {
        return linkMode == null ? LinkMode.REDIRECT : linkMode;
    }


    public Instant expire() {
        return expireAt;
    }

    public Instant resolvedExpireAt() {
        if (expireAt == null) {
            return null;
        }
        if (!expireAt.isAfter(Instant.now())) {
            throw new IllegalArgumentException("expireAt must be greater than the current UTC time");
        }
        return expireAt;
    }

    public String resolvedCustomPath() {
        if (customPath == null) {
            return null;
        }
        if (customPath.isBlank()) {
            throw new IllegalArgumentException("customPath cannot be empty");
        }
        String trimmed = customPath.trim();
        while (trimmed.startsWith("/")) {
            trimmed = trimmed.substring(1).trim();
        }
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1).trim();
        }
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("customPath cannot be empty");
        }
        if (trimmed.length() > 64) {
            throw new IllegalArgumentException("customPath cannot exceed 64 characters");
        }
        if (!CUSTOM_PATH_PATTERN.matcher(trimmed).matches()) {
            throw new IllegalArgumentException("Invalid customPath format: '" + customPath + "'. Must match pattern: " + CUSTOM_PATH_PATTERN.pattern());
        }
        return trimmed;
    }

    @Deprecated
    public String resolvedSlug() {
        return resolvedCustomPath();
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
}