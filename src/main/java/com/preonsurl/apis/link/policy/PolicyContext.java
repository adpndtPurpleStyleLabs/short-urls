package com.preonsurl.apis.link.policy;

import com.preonsurl.apis.link.entity.AccessPolicy;
import com.preonsurl.apis.link.entity.UsagePolicy;
import com.preonsurl.apis.link.policy.model.DeviceInfo;
import jakarta.servlet.http.HttpServletRequest;

import java.time.Instant;

/**
 * Immutable context encapsulating all required data for link policy evaluation.
 * Avoids parameter sprawl and eliminates duplicate request/database parsing across rules.
 */
public record PolicyContext(
        Long shortUrlId,
        String newUrl,
        String originalUrl,
        boolean active,
        Instant expireAt,
        Long usageLimit,
        long clickCount,
        AccessPolicy accessPolicy,
        UsagePolicy usagePolicy,
        HttpServletRequest request,
        String clientIp,
        String clientCountry,
        DeviceInfo deviceInfo,
        String referer,
        boolean verifiedByCookie,
        String submittedPin,
        String submittedPassword,
        boolean isVerificationFlow
) {
    public boolean hasAccessPolicy() {
        return accessPolicy != null;
    }

    public boolean hasUsagePolicy() {
        return usagePolicy != null;
    }

    public boolean isSecured() {
        return accessPolicy != null && accessPolicy.isSecured();
    }

    public boolean isPinOrPasswordProtected() {
        return accessPolicy != null && accessPolicy.isPinOrPasswordProtected();
    }
}
