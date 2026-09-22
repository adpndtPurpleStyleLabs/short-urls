package com.preonsurl.apis.link.dto.CreateRequest;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.preonsurl.apis.link.dto.CreateRequest.AccessPolicies.AccessPolicies;
import com.preonsurl.apis.link.dto.CreateRequest.UsagePolicies.UsagePolicies;
import com.preonsurl.apis.link.enums.LinkMode;
import com.preonsurl.apis.link.enums.UsagePolicyType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.util.List;
import java.util.regex.Pattern;

@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "Request payload for creating a shortened URL")
public record CreateNewUrlRequest(
        @NotBlank(message = "url cannot be empty")
        @Schema(
                description = "Original target URL to shorten",
                example = "https://example.com/products/item1",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        String url,

        @Schema(
                description = "Optional custom path for the shortened URL",
                example = "invoice/diwali-sale",
                requiredMode = Schema.RequiredMode.NOT_REQUIRED
        )
        @JsonAlias({"slug", "path"})
        String customPath,

        @Valid
        @Schema(
                description = "Usage and lifetime policies for the shortened URL",
                requiredMode = Schema.RequiredMode.NOT_REQUIRED
        )
        UsagePolicies usagePolicies,

        @Valid
        @Schema(
                description = "Access-control policies for the shortened URL",
                requiredMode = Schema.RequiredMode.NOT_REQUIRED
        )
        AccessPolicies accessPolicies,

        @Schema(
                description = "Optional note for this shortened URL",
                example = "Diwali campaign landing page",
                requiredMode = Schema.RequiredMode.NOT_REQUIRED
        )
        @JsonAlias({"note", "notes"})
        String notes,

        @Schema(
                description = "Optional tags for organizing and filtering URLs",
                example = "[\"marketing\", \"diwali\", \"campaign\"]",
                requiredMode = Schema.RequiredMode.NOT_REQUIRED
        )
        List<String> tags,

        @Schema(
                description = """
                        How SecureURL delivers the destination.
                        REDIRECT navigates the browser to the destination,
                        IFRAME embeds it,
                        PROXY serves it through SecureURL,
                        MIRROR serves a rewritten copy.
                        """,
                example = "REDIRECT",
                defaultValue = "REDIRECT",
                requiredMode = Schema.RequiredMode.NOT_REQUIRED
        )
        LinkMode linkMode,

        @Schema(
                description = "Whether to generate a short code for this URL",
                example = "true",
                defaultValue = "false",
                requiredMode = Schema.RequiredMode.NOT_REQUIRED
        )
        @JsonAlias({"AddShortCode", "add_short_code"})
        Boolean addShortCode,

        @Schema(
                description = "Legacy expiration timestamp in UTC ISO-8601 format",
                example = "2026-12-31T23:59:59Z",
                requiredMode = Schema.RequiredMode.NOT_REQUIRED
        )
        @JsonAlias({"expire", "expiresAt", "expire_at"})
        Instant expireAt,

        @Schema(
                description = "Legacy usage limit: 'once', 'unlimited', a positive integer, or null",
                example = "5",
                requiredMode = Schema.RequiredMode.NOT_REQUIRED
        )
        @JsonAlias({"limit", "usage_limit"})
        Object usageLimit,

        @Schema(
                description = "Optional custom domain for the shortened URL",
                example = "links.mybrand.com",
                requiredMode = Schema.RequiredMode.NOT_REQUIRED
        )
        @JsonAlias({"customDomain", "custom_domain"})
        String domain

) {

    private static final Pattern CUSTOM_PATH_PATTERN =
            Pattern.compile("^[a-zA-Z0-9_-]+(?:/[a-zA-Z0-9_-]+)*$");

    public CreateNewUrlRequest(
            String url,
            String customPath,
            UsagePolicies usagePolicies,
            AccessPolicies accessPolicies,
            String notes,
            List<String> tags,
            LinkMode linkMode,
            Boolean addShortCode,
            Instant expireAt,
            Object usageLimit
    ) {
        this(url, customPath, usagePolicies, accessPolicies, notes, tags, linkMode, addShortCode, expireAt, usageLimit, null);
    }

    public CreateNewUrlRequest(
            String url,
            String customPath,
            UsagePolicies usagePolicies,
            AccessPolicies accessPolicies,
            String notes,
            List<String> tags,
            LinkMode linkMode,
            Boolean addShortCode
    ) {
        this(url, customPath, usagePolicies, accessPolicies, notes, tags, linkMode, addShortCode, null, null, null);
    }

    public CreateNewUrlRequest(
            String url,
            String customPath,
            Instant expireAt
    ) {
        this(
                url,
                customPath,
                expireAt == null
                        ? UsagePolicies.unlimited()
                        : new UsagePolicies(
                        UsagePolicyType.UNLIMITED,
                        null,
                        expireAt,
                        null
                ),
                AccessPolicies.publicAccess(),
                null,
                null,
                LinkMode.REDIRECT,
                false,
                expireAt,
                null,
                null
        );
    }

    public String resolvedDomain() {
        if (domain == null || domain.isBlank()) {
            return null;
        }
        String d = domain.trim().toLowerCase();
        if (d.startsWith("https://")) d = d.substring(8);
        else if (d.startsWith("http://")) d = d.substring(7);
        int slashIdx = d.indexOf('/');
        if (slashIdx != -1) d = d.substring(0, slashIdx);
        int colonIdx = d.indexOf(':');
        if (colonIdx != -1) d = d.substring(0, colonIdx);
        if (d.endsWith(".")) d = d.substring(0, d.length() - 1);
        return d.isBlank() ? null : d;
    }

    public boolean resolvedAddShortCode() {
        return Boolean.TRUE.equals(addShortCode);
    }

    public LinkMode resolvedLinkMode() {
        return linkMode == null
                ? LinkMode.REDIRECT
                : linkMode;
    }

    public UsagePolicies resolvedUsagePolicies() {
        if (usagePolicies != null) {
            return usagePolicies.validate();
        }

        // Support legacy flat usageLimit / expireAt
        UsagePolicyType type = UsagePolicyType.UNLIMITED;
        Long limit = parseLegacyUsageLimit(usageLimit);
        if (limit != null) {
            if (limit == 1L && "once".equalsIgnoreCase(String.valueOf(usageLimit).trim())) {
                type = UsagePolicyType.ONE_TIME;
            } else {
                type = UsagePolicyType.USAGE_LIMIT;
            }
        }

        Instant resolvedExp = expireAt;
        if (resolvedExp != null && !resolvedExp.isAfter(Instant.now())) {
            throw new IllegalArgumentException("expireAt must be greater than the current UTC time");
        }

        UsagePolicies policies = new UsagePolicies(type, limit, resolvedExp, null);
        return policies.validate();
    }

    public AccessPolicies resolvedAccessPolicies() {
        AccessPolicies ap = accessPolicies == null
                ? AccessPolicies.publicAccess()
                : accessPolicies;
        return ap.validate();
    }

    public Instant resolvedExpireAt() {
        return resolvedUsagePolicies().resolvedExpireAt();
    }

    public Long resolvedUsageLimit() {
        return resolvedUsagePolicies().resolvedUsageLimit();
    }

    private Long parseLegacyUsageLimit(Object rawLimit) {
        if (rawLimit == null) {
            return null;
        }
        if (rawLimit instanceof Number number) {
            long val = number.longValue();
            if (val <= 0) {
                throw new IllegalArgumentException("usageLimit must be a positive integer or 'once' or 'unlimited'");
            }
            return val;
        }
        if (rawLimit instanceof String str) {
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
        throw new IllegalArgumentException("Invalid usageLimit format: " + rawLimit);
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
            throw new IllegalArgumentException(
                    "Invalid customPath format: '" +
                            customPath +
                            "'. Must match pattern: " +
                            CUSTOM_PATH_PATTERN.pattern()
            );
        }

        return trimmed;
    }

    @Deprecated
    public String resolvedSlug() {
        return resolvedCustomPath();
    }

    public void validate() {
        resolvedCustomPath();
        resolvedUsagePolicies();
        resolvedAccessPolicies();
        if (resolvedLinkMode() == LinkMode.PROXY || resolvedLinkMode() == LinkMode.MIRROR) {
            com.preonsurl.apis.link.service.ProxyResourceValidator.validateProxyUrl(url);
        }
    }
}
