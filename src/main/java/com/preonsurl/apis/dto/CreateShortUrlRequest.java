package com.preonsurl.apis.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.Instant;

public record CreateShortUrlRequest(
        @NotBlank(message = "url cannot be empty")
        String url,
        String dirType,
        ExpireRequest expire
) {
    public record ExpireRequest(
            boolean enabled,
            Instant expireAt
    ) {
    }
}
