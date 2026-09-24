package com.preonsurl.apis.link.policy.evaluator;

import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * Evaluates whether a client IP satisfies a configured IP allowlist.
 */
@Component
public class IpPolicyEvaluator {

    private final CidrMatcher cidrMatcher;

    public IpPolicyEvaluator(CidrMatcher cidrMatcher) {
        this.cidrMatcher = cidrMatcher;
    }

    /**
     * Determines whether the given client IP matches any rule in the comma-separated allowlist.
     *
     * @param clientIp The client IP to test
     * @param ipAllowlistStr Comma-separated list of exact IPs or CIDR blocks
     * @return true if allowed, false if denied
     */
    public boolean isAllowed(String clientIp, String ipAllowlistStr) {
        if (ipAllowlistStr == null || ipAllowlistStr.isBlank()) {
            return true; // No restriction configured
        }

        if (clientIp == null || clientIp.isBlank()) {
            return false;
        }

        String[] allowedEntries = ipAllowlistStr.split(",");
        for (String rawEntry : allowedEntries) {
            String entry = rawEntry.trim();
            if (!entry.isEmpty() && cidrMatcher.matches(clientIp, entry)) {
                return true;
            }
        }

        return false;
    }
}
