package com.preonsurl.apis.link.policy.resolver;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Strategy interface for resolving client geographic country from an incoming HTTP request.
 */
public interface CountryResolver {
    String resolveCountry(HttpServletRequest request);
}
