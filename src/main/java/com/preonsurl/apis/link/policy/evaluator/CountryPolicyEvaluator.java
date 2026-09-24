package com.preonsurl.apis.link.policy.evaluator;

import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Locale;

/**
 * Evaluates whether a client's geographic country satisfies the configured country allowlist.
 */
@Component
public class CountryPolicyEvaluator {

    private final CidrMatcher cidrMatcher;

    public CountryPolicyEvaluator(CidrMatcher cidrMatcher) {
        this.cidrMatcher = cidrMatcher;
    }

    /**
     * Determines whether client country satisfies the allowed countries configuration.
     *
     * @param clientCountry The detected 2-letter ISO country code (e.g., "US", "IN")
     * @param allowedCountriesStr Comma-separated list of allowed 2-letter ISO country codes
     * @param clientIp The client IP address (used for loopback/internal dev fallback)
     * @return true if access is permitted, false if blocked
     */
    public boolean isAllowed(String clientCountry, String allowedCountriesStr, String clientIp) {
        if (allowedCountriesStr == null || allowedCountriesStr.isBlank()) {
            return true; // No restriction configured
        }

        // Loopback / Private network fallback when country headers cannot be resolved (local dev/test)
        if ((clientCountry == null || clientCountry.isBlank()) && clientIp != null) {
            if (isLocalOrPrivateNetwork(clientIp)) {
                return true;
            }
        }

        if (clientCountry == null || clientCountry.isBlank()) {
            return false;
        }

        String normalizedClientCountry = clientCountry.trim().toUpperCase(Locale.ROOT);
        String[] allowed = allowedCountriesStr.split(",");

        for (String raw : allowed) {
            String country = raw.trim().toUpperCase(Locale.ROOT);
            if (country.equals(normalizedClientCountry)) {
                return true;
            }
        }

        return false;
    }

    private boolean isLocalOrPrivateNetwork(String ip) {
        String clean = cidrMatcher.cleanIp(ip);
        if (cidrMatcher.isLoopback(clean)) {
            return true;
        }
        return clean.startsWith("10.") || clean.startsWith("192.168.") || clean.startsWith("172.16.")
                || clean.startsWith("172.17.") || clean.startsWith("172.18.") || clean.startsWith("172.19.")
                || clean.startsWith("172.2") || clean.startsWith("172.30.") || clean.startsWith("172.31.");
    }
}
