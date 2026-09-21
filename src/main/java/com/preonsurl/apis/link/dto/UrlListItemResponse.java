package com.preonsurl.apis.link.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

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
        String why
) {
    public UrlListItemResponse(
            String shortLink,
            String originalLink,
            boolean isEnabled,
            boolean isExpired,
            String expiredReason
    ) {
        this(shortLink, originalLink, isEnabled, isExpired, expiredReason, expiredReason);
    }
}
