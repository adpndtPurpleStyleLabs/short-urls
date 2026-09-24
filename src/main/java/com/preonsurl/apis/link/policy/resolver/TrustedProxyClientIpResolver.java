package com.preonsurl.apis.link.policy.resolver;

import com.preonsurl.apis.link.policy.evaluator.CidrMatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * Resolves the genuine client IP address by validating whether the immediate
 * upstream peer is a trusted proxy before accepting X-Forwarded-For or X-Real-IP headers.
 */
@Component
public class TrustedProxyClientIpResolver implements ClientIpResolver {

    private static final Logger log = LoggerFactory.getLogger(TrustedProxyClientIpResolver.class);

    private final CidrMatcher cidrMatcher;
    private final List<String> trustedProxies;
    private final boolean trustAllProxies;

    public TrustedProxyClientIpResolver(
            CidrMatcher cidrMatcher,
            @Value("${preons.security.trusted-proxies:127.0.0.1,::1,10.0.0.0/8,172.16.0.0/12,192.168.0.0/16,fc00::/7}") String trustedProxiesStr,
            @Value("${preons.security.trust-all-proxies:false}") boolean trustAllProxies
    ) {
        this.cidrMatcher = cidrMatcher;
        this.trustAllProxies = trustAllProxies;
        this.trustedProxies = (trustedProxiesStr != null && !trustedProxiesStr.isBlank())
                ? Arrays.stream(trustedProxiesStr.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .toList()
                : List.of("127.0.0.1", "::1");
    }

    @Override
    public String resolveClientIp(HttpServletRequest request) {
        if (request == null) {
            return "127.0.0.1";
        }

        String remoteAddr = request.getRemoteAddr();
        String cleanRemote = cidrMatcher.cleanIp(remoteAddr);

        if (cleanRemote.isEmpty()) {
            cleanRemote = "127.0.0.1";
        }

        // If the immediate peer is not a trusted proxy, reject any spoofed headers from the client
        if (!trustAllProxies && !isTrustedProxy(cleanRemote)) {
            return cleanRemote;
        }

        // 1. Inspect X-Forwarded-For
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            String[] ips = xForwardedFor.split(",");
            // Return first non-empty client IP in the chain
            for (String raw : ips) {
                String candidate = cidrMatcher.cleanIp(raw);
                if (!candidate.isEmpty()) {
                    return candidate;
                }
            }
        }

        // 2. Inspect X-Real-IP
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isBlank()) {
            String candidate = cidrMatcher.cleanIp(xRealIp);
            if (!candidate.isEmpty()) {
                return candidate;
            }
        }

        return cleanRemote;
    }

    public boolean isTrustedProxy(String ip) {
        if (ip == null || ip.isBlank()) {
            return false;
        }
        for (String trustedRange : trustedProxies) {
            if (cidrMatcher.matches(ip, trustedRange)) {
                return true;
            }
        }
        return false;
    }
}
