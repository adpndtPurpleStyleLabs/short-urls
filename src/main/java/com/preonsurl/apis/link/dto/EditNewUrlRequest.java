package com.preonsurl.apis.link.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.preonsurl.apis.link.dto.CreateRequest.AccessPolicies.AccessPolicies;
import com.preonsurl.apis.link.dto.CreateRequest.UsagePolicies.UsagePolicies;
import com.preonsurl.apis.link.enums.LinkMode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.util.List;

@Schema(description = "Request payload for editing an existing shortened URL")
public record EditNewUrlRequest(
        @NotBlank(message = "newUrl cannot be empty")
        @Schema(description = "The short URL identifying the link to edit", example = "http://localhost:8081/invoice/diwali-sale", requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonAlias({"newLink", "shortUrl", "fullUrl"})
        String newUrl,

        @Schema(description = "Updated destination target URL", example = "https://example.com/products/item1-updated", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        @JsonAlias({"url", "targetUrl"})
        String originalUrl,

        @Schema(description = "Updated custom path", example = "invoice/diwali-sale-2", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        @JsonAlias({"path", "custom_path"})
        String customPath,

        @Schema(description = "Updated expiration timestamp in UTC ISO-8601 format. Must be in the future.", example = "2026-12-31T23:59:59Z", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        @JsonAlias({"expire", "expiresAt", "expire_at"})
        Instant expireAt,

        @Schema(description = "Updated usage limit: 'once', 'unlimited', a positive integer, or null for unlimited", example = "10", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        @JsonAlias({"limit", "usage_limit"})
        Object usageLimit,

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
            Boolean isActive
    ) {
        this(newUrl, originalUrl, customPath, expireAt, usageLimit, notes, tags, linkMode, isActive, null, null);
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
