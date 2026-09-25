package com.preonsurl.apis.publiclink.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.preonsurl.apis.link.dto.CreateRequest.AccessPolicies.AccessPolicies;
import com.preonsurl.apis.link.enums.AccessPolicyMode;
import com.preonsurl.apis.publiclink.cache.PublicSecureUrlLruCache;
import com.preonsurl.apis.publiclink.dto.AccessData;
import com.preonsurl.apis.publiclink.dto.CachedPublicSecureUrlDto;
import com.preonsurl.apis.publiclink.dto.CreatePublicLinkRequest;
import com.preonsurl.apis.publiclink.dto.PublicLinkResponse;
import com.preonsurl.apis.publiclink.entity.PublicSecureUrl;
import com.preonsurl.apis.publiclink.event.PublicSecureUrlCreatedEvent;
import com.preonsurl.apis.publiclink.event.PublicSecureUrlServedEvent;
import com.preonsurl.apis.publiclink.ratelimit.PublicLinkRateLimiter;
import com.preonsurl.apis.publiclink.repository.PublicSecureUrlRepository;
import com.preonsurl.core.ShortCodePool;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Service
public class PublicLinkService {

    private static final Logger log = LoggerFactory.getLogger(PublicLinkService.class);
    private static final String BASE62_CHARS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";

    private static final SecureRandom RANDOM = new SecureRandom();

    private final ShortCodePool codePool;
    private final PublicSecureUrlRepository repository;
    private final PublicSecureUrlLruCache lruCache;
    private final PublicLinkRateLimiter rateLimiter;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    @Value("${preonsurl.psecure.domain:http://psecure.domain.com:8081}")
    private String psecureDomain;

    public PublicLinkService(ShortCodePool codePool, PublicSecureUrlRepository repository, PublicSecureUrlLruCache lruCache, PublicLinkRateLimiter rateLimiter, ApplicationEventPublisher eventPublisher) {
        this.codePool = codePool;
        this.repository = repository;
        this.lruCache = lruCache;
        this.rateLimiter = rateLimiter;
        this.eventPublisher = eventPublisher;
        this.objectMapper = new ObjectMapper().findAndRegisterModules();
    }

    // ============================================================
    // CREATE PUBLIC LINK
    // ============================================================

    public PublicLinkResponse createPublicLink(CreatePublicLinkRequest request, Long userId, HttpServletRequest httpRequest) {
        validateRequest(request);
        final String originalUrl = request.url().trim();
        final String shortCode = generateFastShortCode();
        final String psecureUrl = psecureDomain + "/" + shortCode;
        final String linkMode = request.resolvedLinkMode();
        final Instant createdAt = Instant.now();
        final AccessData access = resolveAccessPolicies(request.accessPolicies());

        /*
         * Put into cache before returning.
         *
         * This makes the newly created link immediately resolvable
         * without waiting for database persistence.
         */
        lruCache.put(new CachedPublicSecureUrlDto(null, shortCode, originalUrl, psecureUrl, linkMode, access.pin(), access.password(), access.mode(), null, true, createdAt, 0L));

        /*
         * Persistence happens asynchronously.
         *
         * Nothing database-related blocks the request thread.
         */
        publishAsync(shortCode, originalUrl, psecureUrl, linkMode, access, request.accessPolicies(), extractClientIp(httpRequest), getHeader(httpRequest, "User-Agent"),createdAt, userId);
        return PublicLinkResponse.of(psecureUrl, linkMode, createdAt, -1);
    }

    // ============================================================
    // VALIDATION
    // ============================================================

    private void validateRequest(CreatePublicLinkRequest request) {

        if (request == null) {
            throw new IllegalArgumentException("Request cannot be null");
        }

        if (request.url() == null || request.url().isBlank()) {
            throw new IllegalArgumentException("URL cannot be empty");
        }

        AccessPolicies policies = request.accessPolicies();

        if (policies == null) {
            return;
        }

        if (policies.mode() == AccessPolicyMode.SECURED) {
            boolean hasPin = policies.pin() != null && policies.pin().pin() != null && !policies.pin().pin().isBlank();
            boolean hasPassword = policies.password() != null && policies.password().password() != null && !policies.password().password().isBlank();
            if (!hasPin && !hasPassword) {
                throw new IllegalArgumentException("PIN or Password is required");
            }
        }
    }

    // ============================================================
    // ACCESS POLICY
    // ============================================================

    private AccessData resolveAccessPolicies(AccessPolicies policies) {
        if (policies == null) {
            return new AccessData(null, null, "PUBLIC");
        }
        String pin = null;
        if (policies.pin() != null) {
            String value = policies.pin().pin();

            if (value != null && !value.isBlank()) {
                pin = value.trim();
            }
        }
        final String mode = pin != null ? "PRIVATE" : "PUBLIC";
        return new AccessData(pin, null, mode);
    }

    // ============================================================
    // ASYNC PERSISTENCE
    // ============================================================

    private void publishAsync(
            String shortCode,
            String originalUrl,
            String psecureUrl,
            String linkMode,
            AccessData access,
            AccessPolicies policies,
            String clientIp,
            String userAgent,
            Instant createdAt,
            Long userId) {

        Thread.ofVirtual().start(() -> {

            try {

                String policiesJson = null;

                if (policies != null) {
                    policiesJson =
                            objectMapper.writeValueAsString(policies);
                }

                PublicSecureUrlCreatedEvent event =
                        new PublicSecureUrlCreatedEvent(
                                shortCode,
                                originalUrl,
                                psecureUrl,
                                linkMode,
                                access.pin(),
                                access.password(),
                                access.mode(),
                                policiesJson,
                                clientIp,
                                userAgent,
                                createdAt,
                                userId
                        );

                eventPublisher.publishEvent(event);

            } catch (Exception e) {

                log.error(
                        "Failed to publish public link creation event " +
                                "for shortCode='{}'",
                        shortCode,
                        e
                );
            }
        });
    }

    // ============================================================
    // RESOLVE
    // ============================================================

    public Optional<CachedPublicSecureUrlDto> resolve(
            String shortKey) {

        if (shortKey == null || shortKey.isBlank()) {
            return Optional.empty();
        }

        String key = shortKey.trim();

        while (key.startsWith("/")) {
            key = key.substring(1);
        }

        // 1. Cache
        CachedPublicSecureUrlDto cached =
                lruCache.get(key);

        if (cached != null) {
            return Optional.of(cached);
        }

        // 2. Database
        Optional<PublicSecureUrl> dbOptional =
                repository.findByShortKey(key);

        if (dbOptional.isEmpty()) {
            dbOptional =
                    repository.findByPsecureUrl(key);
        }

        if (dbOptional.isEmpty()) {
            return Optional.empty();
        }

        PublicSecureUrl entity =
                dbOptional.get();

        CachedPublicSecureUrlDto loaded =
                new CachedPublicSecureUrlDto(
                        entity.getId(),
                        entity.getShortKey(),
                        entity.getOriginalUrl(),
                        entity.getPsecureUrl(),
                        entity.getLinkMode(),
                        entity.getPin(),
                        entity.getPassword(),
                        entity.getMode(),
                        entity.getAccessPoliciesJson(),
                        entity.isActive(),
                        entity.getCreatedAt(),
                        entity.getClickCount()
                );

        lruCache.put(loaded);

        return Optional.of(loaded);
    }

    // ============================================================
    // SERVE EVENT
    // ============================================================
    public void recordServeEvent(
            CachedPublicSecureUrlDto cached,
            HttpServletRequest request,
            String status) {

        if (cached == null) {
            return;
        }

        cached.incrementClickCount();
        eventPublisher.publishEvent(
                new PublicSecureUrlServedEvent(
                        cached.getShortKey(),
                        cached.getId(),
                        extractClientIp(request),
                        getHeader(request, "User-Agent"),
                        getHeader(request, "Referer"),
                        Instant.now(),
                        status
                )
        );
    }

    // ============================================================
    // IP
    // ============================================================

    public String extractClientIp(HttpServletRequest request) {
        if (request == null) {
            return "127.0.0.1";
        }
        String ip = request.getHeader("CF-Connecting-IP");
        if (isInvalidIp(ip)) {
            ip = request.getHeader("X-Forwarded-For");
        }

        if (isInvalidIp(ip)) {
            ip = request.getHeader("X-Real-IP");
        }

        if (isInvalidIp(ip)) {
            ip = request.getRemoteAddr();
        }

        if (ip != null) {
            int commaIndex = ip.indexOf(',');
            if (commaIndex >= 0) {
                ip = ip.substring(0, commaIndex);
            }

            ip = ip.trim();
        }

        return ip == null || ip.isBlank() ? "127.0.0.1" : ip;
    }

    private boolean isInvalidIp(String ip) {
        return ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip);
    }

    private String getHeader(HttpServletRequest request, String header) {
        return request != null ? request.getHeader(header) : null;
    }

    // ============================================================
    // SHORT CODE GENERATION
    // ============================================================

    private String generateFastShortCode() {
        try {

            if (codePool != null) {

                String code =
                        codePool.nextCode(
                                2,
                                TimeUnit.MILLISECONDS
                        );

                if (code != null) {
                    return code;
                }

                log.warn(
                        "Short code pool returned no code, " +
                                "falling back to random generation"
                );
            }

            // 7-character fallback
            for (int i = 0; i < 3; i++) {

                String candidate =
                        generateRandomCode(7);

                if (lruCache.get(candidate) == null) {
                    return candidate;
                }
            }

            // 8-character fallback
            String candidate =
                    generateRandomCode(8);

            if (lruCache.get(candidate) == null) {
                return candidate;
            }

            log.error(
                    "Unable to generate unique short code"
            );

            throw new IllegalStateException(
                    "Unable to generate a unique short code"
            );

        } catch (IllegalStateException e) {

            throw e;

        } catch (Exception e) {

            log.error(
                    "Failed to generate short code",
                    e
            );

            throw new IllegalStateException(
                    "Unable to generate short code",
                    e
            );
        }
    }

    // ============================================================
    // RANDOM FALLBACK
    // ============================================================

    private String generateRandomCode(int length) {

        StringBuilder sb =
                new StringBuilder(length);

        for (int i = 0; i < length; i++) {

            sb.append(
                    BASE62_CHARS.charAt(
                            RANDOM.nextInt(
                                    BASE62_CHARS.length()
                            )
                    )
            );
        }

        return sb.toString();
    }

    // ============================================================
    // RATE LIMITER
    // ============================================================

    public PublicLinkRateLimiter getRateLimiter() {
        return rateLimiter;
    }
}