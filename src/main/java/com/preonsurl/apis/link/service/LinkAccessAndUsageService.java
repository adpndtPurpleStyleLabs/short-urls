package com.preonsurl.apis.link.service;

import com.preonsurl.apis.link.dto.PolicyEvaluationResult;
import com.preonsurl.apis.link.entity.NewUrl;
import com.preonsurl.apis.link.policy.LinkPolicyEngine;
import com.preonsurl.apis.link.policy.PolicyContext;
import com.preonsurl.apis.link.policy.PolicyContextFactory;
import com.preonsurl.apis.link.policy.resolver.ClientIpResolver;
import com.preonsurl.apis.link.policy.security.SecurityVerificationService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Application service facade for evaluating URL access and usage policies.
 * Delegates context assembly to PolicyContextFactory and rule orchestration to LinkPolicyEngine.
 */
@Service
public class LinkAccessAndUsageService {

    private final PolicyContextFactory contextFactory;
    private final LinkPolicyEngine policyEngine;
    private final ClientIpResolver clientIpResolver;
    private final SecurityVerificationService securityVerificationService;

    public LinkAccessAndUsageService(
            PolicyContextFactory contextFactory,
            LinkPolicyEngine policyEngine,
            ClientIpResolver clientIpResolver,
            SecurityVerificationService securityVerificationService
    ) {
        this.contextFactory = contextFactory;
        this.policyEngine = policyEngine;
        this.clientIpResolver = clientIpResolver;
        this.securityVerificationService = securityVerificationService;
    }

    /**
     * Evaluates all usage and access policies for an existing NewUrl entity.
     *
     * @param entity The short URL entity
     * @param request The incoming HTTP servlet request
     * @return PolicyEvaluationResult describing whether the request is allowed, requires challenge, or is rejected.
     */
    public PolicyEvaluationResult evaluatePolicies(NewUrl entity, HttpServletRequest request) {
        if (entity == null) {
            return PolicyEvaluationResult.notFound("New URL not found");
        }

        PolicyContext context = contextFactory.createContext(entity, request);
        return policyEngine.evaluate(context);
    }

    /**
     * Evaluates all usage and access policies for a URL with direct parameters (e.g. from LRU cache).
     */
    public PolicyEvaluationResult evaluatePolicies(
            Long shortUrlId,
            String newUrl,
            String originalUrl,
            boolean active,
            Instant expireAt,
            Long usageLimit,
            long clickCount,
            HttpServletRequest request
    ) {
        PolicyContext context = contextFactory.createContext(
                shortUrlId,
                newUrl,
                originalUrl,
                active,
                expireAt,
                usageLimit,
                clickCount,
                request
        );
        return policyEngine.evaluate(context);
    }

    /**
     * Validates credentials (PIN and/or Password) against a secured link's AccessPolicy.
     * Environmental rules are evaluated prior to credentials, short-circuiting on any environmental violation.
     */
    public PolicyEvaluationResult verifyCredentials(
            NewUrl entity,
            String pin,
            String password,
            HttpServletRequest request
    ) {
        if (entity == null) {
            return PolicyEvaluationResult.notFound("New URL not found");
        }

        PolicyContext context = contextFactory.createContextForVerification(entity, pin, password, request);
        return policyEngine.evaluate(context);
    }

    /**
     * Helper delegation for trusted client IP resolution.
     */
    public String extractClientIp(HttpServletRequest request) {
        return clientIpResolver.resolveClientIp(request);
    }

    /**
     * Helper delegation for checking security verification cookie.
     */
    public boolean isVerifiedByCookie(HttpServletRequest request, Long shortUrlId) {
        return securityVerificationService.isVerifiedByCookie(request, shortUrlId);
    }

    /**
     * Helper delegation for creating security challenge authorization cookie.
     */
    public ResponseCookie createVerificationCookie(Long shortUrlId) {
        return securityVerificationService.createVerificationCookie(shortUrlId);
    }
}
