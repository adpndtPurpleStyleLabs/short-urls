package com.preonsurl.apis.link.policy.resolver;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Strategy interface for resolving the true client IP address from an incoming HTTP request.
 */
public interface ClientIpResolver {
    String resolveClientIp(HttpServletRequest request);
}
