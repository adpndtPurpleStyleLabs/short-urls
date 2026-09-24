package com.preonsurl.apis.link.policy.evaluator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Locale;

/**
 * Boundary-safe evaluator for HTTP Referrer allowlist policies.
 * Supports exact domains, subdomain hierarchies, wildcard prefixes (*.domain.com),
 * and specific URL prefixes, while strictly guarding against domain-spoofing attacks
 * (such as evil-example.com or example.com.attacker.com).
 */
@Component
public class ReferrerPolicyEvaluator {

    private static final Logger log = LoggerFactory.getLogger(ReferrerPolicyEvaluator.class);

    /**
     * Determines whether the HTTP Referer header satisfies the configured allowlist.
     *
     * @param referer The raw incoming Referer header value
     * @param allowedReferrersStr Comma-separated list of allowed domains, wildcards, or URL prefixes
     * @return true if authorized, false if blocked
     */
    public boolean isAllowed(String referer, String allowedReferrersStr) {
        if (allowedReferrersStr == null || allowedReferrersStr.isBlank()) {
            return true; // No restriction configured
        }

        if (referer == null || referer.isBlank()) {
            return false; // Referrer required but missing
        }

        String lowerReferer = referer.trim().toLowerCase(Locale.ROOT);
        String clientHost;
        try {
            URI refererUri = URI.create(lowerReferer);
            clientHost = refererUri.getHost();
        } catch (Exception e) {
            log.debug("Unparseable client referer URI: '{}'", referer);
            return false;
        }

        if (clientHost == null || clientHost.isBlank()) {
            return false;
        }

        String[] allowedEntries = allowedReferrersStr.split(",");
        for (String rawPattern : allowedEntries) {
            String pattern = rawPattern.trim().toLowerCase(Locale.ROOT);
            if (pattern.isEmpty()) {
                continue;
            }

            if (matchesPattern(lowerReferer, clientHost, pattern)) {
                return true;
            }
        }

        return false;
    }

    private boolean matchesPattern(String clientReferer, String clientHost, String pattern) {
        // 1. If pattern is a specific URL with path/query
        if (pattern.startsWith("http://") || pattern.startsWith("https://")) {
            try {
                URI pUri = URI.create(pattern);
                String path = pUri.getPath();
                boolean hasSpecificPath = (path != null && !path.isEmpty() && !"/".equals(path))
                        || pUri.getQuery() != null;

                if (hasSpecificPath) {
                    // Strict URL prefix pattern with path boundary check
                    if (clientReferer.startsWith(pattern)) {
                        if (clientReferer.length() == pattern.length()) {
                            return true;
                        }
                        char nextChar = clientReferer.charAt(pattern.length());
                        return pattern.endsWith("/") || nextChar == '/' || nextChar == '?' || nextChar == '#';
                    }
                    return false;
                }

                if (pUri.getHost() != null) {
                    pattern = pUri.getHost();
                }
            } catch (Exception ignored) {
            }
        }

        // Clean pattern to extract hostname/domain for host-level comparison
        String patternHost = pattern;
        int slashIdx = patternHost.indexOf('/');
        if (slashIdx >= 0) {
            patternHost = patternHost.substring(0, slashIdx);
        }

        // 2. Wildcard Domain Match (*.example.com)
        if (patternHost.startsWith("*.")) {
            String rootDomain = patternHost.substring(2);
            return matchesHostWithBoundary(clientHost, rootDomain);
        }

        // 3. Exact Domain Match or Subdomain Match (example.com)
        return matchesHostWithBoundary(clientHost, patternHost);
    }

    /**
     * Boundary-safe host comparison.
     * Guarantees that targetDomain matches clientHost exactly or as a dot-delimited subdomain suffix.
     * Prevents:
     * - evil-example.com (does NOT match example.com)
     * - notexample.com (does NOT match example.com)
     * - example.com.attacker.com (does NOT match example.com)
     */
    public boolean matchesHostWithBoundary(String clientHost, String targetDomain) {
        if (clientHost == null || targetDomain == null || targetDomain.isBlank()) {
            return false;
        }

        // Exact host match
        if (clientHost.equalsIgnoreCase(targetDomain)) {
            return true;
        }

        // Subdomain match: must end with .targetDomain
        String suffix = "." + targetDomain.toLowerCase(Locale.ROOT);
        return clientHost.toLowerCase(Locale.ROOT).endsWith(suffix);
    }
}
