package com.preonsurl.apis.link.event;

public record ShortUrlServedEvent(
        Long shortUrlId,
        String newUrl,
        String ipAddress,
        String userAgent,
        String referer
) {
}
