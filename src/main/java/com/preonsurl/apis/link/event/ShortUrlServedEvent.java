package com.preonsurl.apis.link.event;

public record ShortUrlServedEvent(
        Long shortUrlId,
        String fullShortUrl,
        String ipAddress,
        String userAgent,
        String referer
) {
}
