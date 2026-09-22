package com.preonsurl.apis.link.service;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Cookie transformation service for MIRROR mode.
 * Transforms upstream Set-Cookie headers by stripping upstream domain directives
 * so cookies are accepted by the browser on the PreonsURL mirror host.
 *
 * <p><b>V1 Limitations:</b>
 * Cross-domain session synchronization, OAuth state binding to original origin,
 * and cookies with strict domain-prefixed attributes (__Host-) are not fully
 * supported in V1.
 */
@Component
public class MirrorCookieService {

    /**
     * Transforms an upstream Set-Cookie header string for mirror delivery.
     *
     * @param rawSetCookie The Set-Cookie header value from upstream
     * @param shortCode    The shortCode or path identifier of the mirror
     * @return The sanitized Set-Cookie header value
     */
    public String transformSetCookie(String rawSetCookie, String shortCode) {
        if (rawSetCookie == null || rawSetCookie.isBlank()) {
            return rawSetCookie;
        }

        String[] parts = rawSetCookie.split(";");
        if (parts.length == 0) {
            return rawSetCookie;
        }

        List<String> retainedParts = new ArrayList<>();
        // First part is always name=value
        retainedParts.add(parts[0].trim());

        for (int i = 1; i < parts.length; i++) {
            String part = parts[i].trim();
            if (part.isEmpty()) {
                continue;
            }

            String lower = part.toLowerCase(Locale.ROOT);
            // Strip incompatible Domain attribute
            if (lower.startsWith("domain=")) {
                continue;
            }

            retainedParts.add(part);
        }

        return String.join("; ", retainedParts);
    }
}
