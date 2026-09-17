package com.preonsurl.apis.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;

import java.time.Instant;

public record CreateShortUrlRequest(
        @NotBlank(message = "url cannot be empty")
        String url,
        String dirType,
        ExpireRequest expire,
        Long usageLimit
) {
    public CreateShortUrlRequest(String url, String dirType, ExpireRequest expire) {
        this(url, dirType, expire, null);
    }
    public record ExpireRequest(
            boolean enabled,
            Instant expireAt
    ) {
    }
}
