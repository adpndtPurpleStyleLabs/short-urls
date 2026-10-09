package com.preonsurl.apis.link.dto;

import java.time.LocalDateTime;

public record LinkAccessLogResponse(
        Long id,
        Long shortUrlId,
        String shortCode,
        String ipAddress,
        String userAgent,
        String referer,
        LocalDateTime accessedAt,
        String country,
        String city,
        Double latitude,
        Double longitude,
        String device,
        String browser,
        String os,
        String region
) {
    public LinkAccessLogResponse(Long id, Long shortUrlId, String shortCode, String ipAddress, String userAgent, String referer, LocalDateTime accessedAt) {
        this(id, shortUrlId, shortCode, ipAddress, userAgent, referer, accessedAt, null, null, null, null, null, null, null, null);
    }

    public LinkAccessLogResponse(Long id, Long shortUrlId, String shortCode, String ipAddress, String userAgent, String referer,
                                 LocalDateTime accessedAt, String country, String city, Double latitude, Double longitude,
                                 String device, String browser, String os) {
        this(id, shortUrlId, shortCode, ipAddress, userAgent, referer, accessedAt, country, city, latitude, longitude, device, browser, os, null);
    }
}
