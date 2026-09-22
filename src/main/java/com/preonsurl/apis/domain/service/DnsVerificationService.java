package com.preonsurl.apis.domain.service;

public interface DnsVerificationService {
    /**
     * Resolves the CNAME target for the given domain.
     *
     * @param domain Domain name (e.g. "links.example.com")
     * @return Target domain (e.g. "go.domain.com") without trailing dot, or null if not found.
     */
    String resolveCname(String domain);
}
