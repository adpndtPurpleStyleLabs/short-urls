package com.preonsurl.apis.link.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.preonsurl.apis.link.dto.CreateRequest.AccessPolicies.AccessPolicies;
import com.preonsurl.apis.link.dto.CreateRequest.UsagePolicies.UsagePolicies;
import com.preonsurl.apis.link.enums.LinkMode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;

import java.time.Instant;
import java.util.List;

@Schema(description = "Request payload for editing an existing shortened URL")
public record EditNewUrlRequest(
        @Schema(description = "The short URL identifying the link to edit", example = "http://localhost:8081/invoice/diwali-sale", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        @JsonAlias({"newLink", "shortUrl", "fullUrl"})
        String newUrl,

        @Schema(description = "Public identifier of the link to edit", example = "f8K2mP9xQ7La3VnR6Tc1Zw", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        @JsonAlias({"id", "public_id"})
        String publicId,

        @Schema(description = "Short code / slug of the link to edit or update", example = "diwali-sale", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        @JsonAlias({"code", "short_code"})
        String shortCode,

        @Schema(description = "Updated destination target URL", example = "https://example.com/products/item1-updated", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        @JsonAlias({"url", "targetUrl", "destinationUrl"})
        String originalUrl,

        @Schema(description = "Updated custom path / slug", example = "invoice/diwali-sale-2", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        @JsonAlias({"path", "custom_path", "slug"})
        String customPath,

        @Schema(description = "Updated custom domain for the shortened URL", example = "links.mybrand.com", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        @JsonAlias({"customDomain", "custom_domain"})
        String domain,

        @Schema(description = "Updated expiration timestamp in UTC ISO-8601 format. Must be in the future.", example = "2026-12-31T23:59:59Z", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        @JsonAlias({"expire", "expiresAt", "expire_at"})
        Instant expireAt,

        @Schema(description = "Whether to remove expiration date (set to unlimited/never expire)", example = "false", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        @JsonAlias({"clear_expire", "clear_expire_at", "clearExpiry"})
        Boolean clearExpireAt,

        @Schema(description = "Updated usage limit: 'once', 'unlimited', a positive integer, or null for unlimited", example = "10", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        @JsonAlias({"limit", "usage_limit"})
        Object usageLimit,

        @Schema(description = "Whether to reset click count and current usage to 0", example = "true", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        @JsonAlias({"resetClicks", "reset_click_count", "reset_clicks", "resetUsage"})
        Boolean resetClickCount,

        @Schema(description = "Explicitly set the click count and current usage counter", example = "0", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        @JsonAlias({"clicks", "click_count"})
        Long clickCount,

        @Schema(description = "Updated note for this shortened URL", example = "Updated Diwali campaign notes", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        @JsonAlias({"note", "noteText"})
        String notes,

        @Schema(description = "Updated tags for organizing and filtering URLs", example = "[\"marketing\", \"sale\"]", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        List<String> tags,

        @Schema(description = "Updated delivery mode (REDIRECT, IFRAME, PROXY, MIRROR)", example = "REDIRECT", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        @JsonAlias({"mode", "link_mode"})
        LinkMode linkMode,

        @Schema(description = "Whether the URL is active", example = "true", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        @JsonAlias({"active", "is_active"})
        Boolean isActive,

        @Valid
        @Schema(description = "Updated usage and lifetime policies for the shortened URL", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        @JsonAlias({"usage_policies", "usagePolicy"})
        UsagePolicies usagePolicies,

        @Valid
        @Schema(description = "Updated access-control policies for the shortened URL", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        @JsonAlias({"access_policies", "accessPolicy"})
        AccessPolicies accessPolicies
) {
    public EditNewUrlRequest(
            String newUrl,
            String originalUrl,
            String customPath,
            Instant expireAt,
            Object usageLimit,
            String notes,
            List<String> tags,
            LinkMode linkMode,
            Boolean isActive,
            UsagePolicies usagePolicies,
            AccessPolicies accessPolicies
    ) {
        this(newUrl, null, null, originalUrl, customPath, null, expireAt, null, usageLimit, null, null, notes, tags, linkMode, isActive, usagePolicies, accessPolicies);
    }

    public EditNewUrlRequest(
            String newUrl,
            String originalUrl,
            String customPath,
            Instant expireAt,
            Object usageLimit,
            String notes,
            List<String> tags,
            LinkMode linkMode,
            Boolean isActive
    ) {
        this(newUrl, originalUrl, customPath, expireAt, usageLimit, notes, tags, linkMode, isActive, null, null);
    }

    public boolean hasIdentifier() {
        return (newUrl != null && !newUrl.isBlank())
                || (publicId != null && !publicId.isBlank())
                || (shortCode != null && !shortCode.isBlank());
    }

    public boolean hasUsagePolicies() {
        return usagePolicies != null;
    }

    public boolean hasAccessPolicies() {
        return accessPolicies != null;
    }

    public boolean hasUsageLimit() {
        return usageLimit != null;
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
