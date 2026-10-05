package com.preonsurl.apis.link.event;

public record ShortUrlServedEvent(
        Long shortUrlId,
        String newUrl,
        String ipAddress,
        String userAgent,
        String referer,
        Double latitude,
        Double longitude,
        Double accuracy
) {
    public ShortUrlServedEvent(Long shortUrlId, String newUrl, String ipAddress, String userAgent, String referer) {
        this(shortUrlId, newUrl, ipAddress, userAgent, referer, null, null, null);
    }

    public ShortUrlServedEvent(Long shortUrlId, String newUrl, String ipAddress, String userAgent, String referer, Double latitude, Double longitude) {
        this(shortUrlId, newUrl, ipAddress, userAgent, referer, latitude, longitude, null);
    }
}

