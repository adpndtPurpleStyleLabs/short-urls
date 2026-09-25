package com.preonsurl.apis.publiclink.event;

import java.time.Instant;

public record PublicSecureUrlCreatedEvent(
        String shortKey,
        String originalUrl,
        String psecureUrl,
        String linkMode,
        String pin,
        String password,
        String mode,
        String accessPoliciesJson,
        String ipAddress,
        String userAgent,
        Instant createdAt,
        Long userId
) {}
