package com.preonsurl.apis.link.dto;

import java.time.LocalDateTime;

public record LinkAccessLogResponse(
        Long id,
        Long shortUrlId,
        String shortCode,
        String ipAddress,
        String userAgent,
        String referer,
        LocalDateTime accessedAt
) {
}
