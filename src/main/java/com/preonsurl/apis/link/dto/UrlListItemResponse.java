package com.preonsurl.apis.link.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Paginated URL list item response")
public record UrlListItemResponse(
        @Schema(description = "Full short link URL", example = "http://localhost:8081/promo")
        String shortLink,

        @Schema(description = "Original destination link", example = "https://example.com/item")
        String originalLink,

        @Schema(description = "Whether the short link is enabled", example = "true")
        @JsonProperty("isEnabled")
        boolean isEnabled,

        @Schema(description = "Whether the short link is expired", example = "false")
        @JsonProperty("isExpired")
        boolean isExpired,

        @Schema(description = "Reason for expiration if expired (TIME, USAGE, or TIME, USAGE)", example = "TIME")
        String expiredReason,

        @Schema(description = "Reason for expiration alias if expired (TIME, USAGE, or TIME, USAGE)", example = "TIME")
        String why,

        @Schema(description = "Total number of times the URL has been clicked", example = "1200")
        long timesClicked,

        @Schema(description = "Timestamp when the link was created")
        Instant createdAt,

        @Schema(description = "Human-readable time ago when the link was created", example = "2 days ago")
        String createdAgo,

        @Schema(description = "Timestamp when the link was last accessed/used")
        Instant lastUsedAt,

        @Schema(description = "Human-readable time ago when the link was last accessed/used", example = "2 hours ago")
        String lastUsedAgo,

        @Schema(description = "Public unique identifier for the link", example = "f8K2mP9xQ7La3VnR6Tc1Zw")
        String publicId,

        @Schema(description = "Tags associated with the short link", example = "[\"marketing\", \"q3\"]")
        java.util.List<String> tags,

        @Schema(description = "Creation source: API or UI", example = "UI")
        String createdBy
) {
    public UrlListItemResponse(
            String shortLink,
            String originalLink,
            boolean isEnabled,
            boolean isExpired,
            String expiredReason,
            String why,
            long timesClicked,
            Instant createdAt,
            String createdAgo,
            Instant lastUsedAt,
            String lastUsedAgo,
            String publicId,
            java.util.List<String> tags
    ) {
        this(shortLink, originalLink, isEnabled, isExpired, expiredReason, why, timesClicked, createdAt, createdAgo, lastUsedAt, lastUsedAgo, publicId, tags, "UI");
    }

    public UrlListItemResponse(
            String shortLink,
            String originalLink,
            boolean isEnabled,
            boolean isExpired,
            String expiredReason
    ) {
        this(shortLink, originalLink, isEnabled, isExpired, expiredReason, expiredReason, 0L, null, null, null, null, null, java.util.List.of(), "UI");
    }

    public UrlListItemResponse(
            String shortLink,
            String originalLink,
            boolean isEnabled,
            boolean isExpired,
            String expiredReason,
            String why
    ) {
        this(shortLink, originalLink, isEnabled, isExpired, expiredReason, why, 0L, null, null, null, null, null, java.util.List.of());
    }

    public UrlListItemResponse(
            String shortLink,
            String originalLink,
            boolean isEnabled,
            boolean isExpired,
            String expiredReason,
            String why,
            long timesClicked,
            Instant createdAt,
            String createdAgo,
            Instant lastUsedAt,
            String lastUsedAgo
    ) {
        this(shortLink, originalLink, isEnabled, isExpired, expiredReason, why, timesClicked, createdAt, createdAgo, lastUsedAt, lastUsedAgo, null, java.util.List.of());
    }

    public UrlListItemResponse(
            String shortLink,
            String originalLink,
            boolean isEnabled,
            boolean isExpired,
            String expiredReason,
            String why,
            long timesClicked,
            Instant createdAt,
            String createdAgo,
            Instant lastUsedAt,
            String lastUsedAgo,
            String publicId
    ) {
        this(shortLink, originalLink, isEnabled, isExpired, expiredReason, why, timesClicked, createdAt, createdAgo, lastUsedAt, lastUsedAgo, publicId, java.util.List.of());
    }
}
