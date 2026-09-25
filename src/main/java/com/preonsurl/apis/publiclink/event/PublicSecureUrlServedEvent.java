package com.preonsurl.apis.publiclink.event;

import java.time.Instant;

public record PublicSecureUrlServedEvent(
        String shortKey,
        Long publicSecureUrlId,
        String ipAddress,
        String userAgent,
        String referer,
        Instant accessedAt,
        String status
) {}
