package com.preonsurl.apis.event;

public record ShortUrlServedEvent(
        Long shortUrlId,
        String shortCode,
        String dirType,
        String ipAddress,
        String userAgent,
        String referer
) {
}
