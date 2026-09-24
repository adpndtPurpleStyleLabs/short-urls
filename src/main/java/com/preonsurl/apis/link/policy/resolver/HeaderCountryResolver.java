package com.preonsurl.apis.link.policy.resolver;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Resolves the client country code from standard edge/CDN/reverse proxy headers.
 */
@Component
public class HeaderCountryResolver implements CountryResolver {

    private static final String[] COUNTRY_HEADERS = {
            "CF-IPCountry",                 // Cloudflare
            "X-Country-Code",               // Standard Reverse Proxies
            "CloudFront-Viewer-Country",    // AWS CloudFront
            "GEOIP-COUNTRY-CODE",           // Nginx GeoIP
            "X-GeoIP-Country",              // HAProxy / Apache GeoIP
            "Fastly-Client-IP-Country"      // Fastly
    };

    @Override
    public String resolveCountry(HttpServletRequest request) {
        if (request == null) {
            return null;
        }

        for (String headerName : COUNTRY_HEADERS) {
            String country = request.getHeader(headerName);
            if (country != null && !country.isBlank()) {
                String normalized = country.trim().toUpperCase(Locale.ROOT);
                if (isValidCountryCode(normalized)) {
                    return normalized;
                }
            }
        }

        return null;
    }

    private boolean isValidCountryCode(String code) {
        if (code == null || code.length() != 2) {
            return false;
        }
        // Exclude unknown/Tor codes if standard
        if ("XX".equals(code) || "T1".equals(code) || "??".equals(code)) {
            return false;
        }
        return code.chars().allMatch(Character::isLetter);
    }
}
