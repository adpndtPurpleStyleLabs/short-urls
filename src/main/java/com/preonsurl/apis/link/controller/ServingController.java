package com.preonsurl.apis.link.controller;

import com.preonsurl.apis.link.dto.ApiResponse;
import com.preonsurl.apis.link.entity.AccessPolicy;
import com.preonsurl.apis.link.entity.NewUrl;
import com.preonsurl.apis.link.entity.UsagePolicy;
import com.preonsurl.apis.link.event.ShortUrlServedEvent;
import com.preonsurl.apis.link.exception.UrlExpiredException;
import com.preonsurl.apis.link.exception.UrlUsageLimitExceededException;
import com.preonsurl.apis.link.repository.AccessPolicyRepository;
import com.preonsurl.apis.link.repository.NewUrlRepository;
import com.preonsurl.apis.link.repository.UsagePolicyRepository;
import com.preonsurl.apis.link.enums.LinkMode;
import com.preonsurl.apis.link.service.NewUrlServingCacheService;
import com.preonsurl.apis.link.service.ProxyService;
import com.preonsurl.apis.link.ui.LinkUiRenderer;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

@Tag(name = "New URL Redirection", description = "Endpoints for redirecting New URLs to destination target URLs")
@Controller
public class ServingController {

    private static final Logger log = LoggerFactory.getLogger(ServingController.class);

    private final NewUrlServingCacheService servingCacheService;
    private final NewUrlRepository repository;
    private final AccessPolicyRepository accessPolicyRepository;
    private final UsagePolicyRepository usagePolicyRepository;
    private final LinkUiRenderer linkUiRenderer;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventPublisher;
    private final ProxyService proxyService;

    public ServingController(NewUrlServingCacheService servingCacheService,
                             NewUrlRepository repository,
                             AccessPolicyRepository accessPolicyRepository,
                             UsagePolicyRepository usagePolicyRepository,
                             LinkUiRenderer linkUiRenderer,
                             PasswordEncoder passwordEncoder,
                             ApplicationEventPublisher eventPublisher,
                             ProxyService proxyService) {
        this.servingCacheService = servingCacheService;
        this.repository = repository;
        this.accessPolicyRepository = accessPolicyRepository;
        this.usagePolicyRepository = usagePolicyRepository;
        this.linkUiRenderer = linkUiRenderer;
        this.passwordEncoder = passwordEncoder;
        this.eventPublisher = eventPublisher;
        this.proxyService = proxyService;
    }

    @Operation(summary = "Service Health Check", description = "Returns service health status")
    @GetMapping("/health")
    @ResponseBody
    public ResponseEntity<?> root() {
        return ResponseEntity.ok(Map.of("service", "007", "status", "UP"));
    }

    @Operation(
            summary = "Redirect root New URL",
            description = "Resolves the given new code using LRU cache and redirects with HTTP 302 Found to destination URL. Returns 410 Gone if expired or usage limit exceeded."
    )
    @GetMapping("/**")
    public ResponseEntity<?> serve(HttpServletRequest request) {
        String fullUrl = request.getRequestURL().toString();
        String path = "";
        if (request.getRequestURI().startsWith("/")) {
            path = request.getRequestURI().substring(1);
        }
        if (path.isBlank()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error("New URL not found"));
        }

        String ipAddress = extractClientIp(request);
        String userAgent = request.getHeader("User-Agent");
        String referer = request.getHeader("Referer");

        // 1. Check LRU Cache first
        com.preonsurl.apis.link.cache.CachedNewUrlDto cached = servingCacheService.getLruCache().get(fullUrl);
        if (cached != null) {
            if (!cached.isActive()) {
                servingCacheService.getLruCache().remove(fullUrl);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error("New URL not found"));
            }
            if (cached.isExpired()) {
                servingCacheService.getLruCache().remove(fullUrl);
                if (isBrowserHtmlRequest(request)) {
                    return ResponseEntity.status(HttpStatus.GONE)
                            .contentType(MediaType.TEXT_HTML)
                            .body(linkUiRenderer.renderExhaustedPage("Link Has Expired", "This short link has expired and is no longer accessible.", "Expired"));
                }
                return ResponseEntity.status(HttpStatus.GONE).body(ApiResponse.error("New URL has expired"));
            }
            if (cached.isUsageLimitBreached()) {
                servingCacheService.getLruCache().remove(fullUrl);
                if (isBrowserHtmlRequest(request)) {
                    return ResponseEntity.status(HttpStatus.GONE)
                            .contentType(MediaType.TEXT_HTML)
                            .body(linkUiRenderer.renderExhaustedPage("Usage Limit Reached", "This short link has reached its maximum allowed number of accesses and is no longer accessible.", "Limit Reached"));
                }
                return ResponseEntity.status(HttpStatus.GONE).body(ApiResponse.error("New URL usage limit reached"));
            }

            // Check if secured by PIN or password
            Optional<AccessPolicy> apOpt = accessPolicyRepository.findByShortUrlId(cached.getId());
            if (apOpt.isPresent() && apOpt.get().isPinOrPasswordProtected()) {
                AccessPolicy policy = apOpt.get();
                if (!isVerifiedByCookie(request, cached.getId())) {
                    return ResponseEntity.ok()
                            .contentType(MediaType.TEXT_HTML)
                            .body(linkUiRenderer.renderSecurityChallenge(path, policy.hasPin(), policy.hasPassword(), null));
                }
            }

            // Serve from cache
            try {
                Optional<String> originalUrl = servingCacheService.resolveAndServe(fullUrl, ipAddress, userAgent, referer);
                if (originalUrl.isPresent()) {
                    String target = originalUrl.get();
                    LinkMode mode = cached.getLinkMode() != null ? cached.getLinkMode() : LinkMode.REDIRECT;
                    if (mode == LinkMode.PROXY) {
                        log.info("Proxying root fullUrl='{}' -> '{}' [IP={}]", fullUrl, target, ipAddress);
                        return proxyService.proxyRequest(target, request);
                    }
                    return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(target)).build();
                }
            } catch (UrlExpiredException e) {
                if (isBrowserHtmlRequest(request)) {
                    return ResponseEntity.status(HttpStatus.GONE).contentType(MediaType.TEXT_HTML)
                            .body(linkUiRenderer.renderExhaustedPage("Link Has Expired", "This short link has expired and is no longer accessible.", "Expired"));
                }
                return ResponseEntity.status(HttpStatus.GONE).body(ApiResponse.error("New URL has expired"));
            } catch (UrlUsageLimitExceededException e) {
                if (isBrowserHtmlRequest(request)) {
                    return ResponseEntity.status(HttpStatus.GONE).contentType(MediaType.TEXT_HTML)
                            .body(linkUiRenderer.renderExhaustedPage("Usage Limit Reached", "This short link has reached its maximum allowed number of accesses.", "Limit Reached"));
                }
                return ResponseEntity.status(HttpStatus.GONE).body(ApiResponse.error("New URL usage limit reached"));
            }
        }

        // 2. Cache Miss: Locate entity by fullUrl, shortCode, or customPath in DB
        Optional<NewUrl> entityOpt = repository.findByNewUrl(fullUrl);
        if (entityOpt.isEmpty()) {
            entityOpt = repository.findByShortCode(path);
        }
        if (entityOpt.isEmpty()) {
            entityOpt = repository.findByCustomPath(path);
        }

        if (entityOpt.isEmpty()) {
            log.warn("Full url not found: '{}' [IP={}]", path, ipAddress);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error("New URL not found"));
        }

        NewUrl entity = entityOpt.get();
        if (!entity.isActive()) {
            log.warn("Short URL is inactive: url='{}'", entity.getOriginalUrl());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error("New URL not found"));
        }

        // 3. Check Usage Policy & Expiration
        Optional<UsagePolicy> usagePolicyOpt = usagePolicyRepository.findByShortUrlId(entity.getId());
        boolean isExpired = (entity.getExpireAt() != null && Instant.now().isAfter(entity.getExpireAt()))
                || (usagePolicyOpt.isPresent() && usagePolicyOpt.get().isExpired());
        boolean isLimitReached = (entity.getUsageLimit() != null && entity.getClickCount() >= entity.getUsageLimit())
                || (usagePolicyOpt.isPresent() && usagePolicyOpt.get().isUsageLimitReached());
        boolean isOutsideSchedule = usagePolicyOpt.isPresent() && usagePolicyOpt.get().isOutsideSchedule();

        if (isExpired || isLimitReached || isOutsideSchedule) {
            log.warn("Link exhausted: expired={}, limitReached={}, outsideSchedule={}, url='{}'",
                    isExpired, isLimitReached, isOutsideSchedule, entity.getNewUrl());

            if (isBrowserHtmlRequest(request)) {
                String title = isExpired ? "Link Has Expired" : isLimitReached ? "Usage Limit Reached" : "Link Outside Schedule";
                String desc = isExpired
                        ? "This short link expired on " + entity.getExpireAt() + " and is no longer accessible."
                        : isLimitReached
                        ? "This short link has reached its maximum allowed number of accesses and is no longer accessible."
                        : "This short link is not currently accessible according to its access schedule.";
                String badge = isExpired ? "Expired" : isLimitReached ? "Limit Reached" : "Schedule Inactive";

                HttpStatus status = isOutsideSchedule ? HttpStatus.FORBIDDEN : HttpStatus.GONE;
                return ResponseEntity.status(status)
                        .contentType(MediaType.TEXT_HTML)
                        .body(linkUiRenderer.renderExhaustedPage(title, desc, badge));
            } else {
                if (isExpired) {
                    return ResponseEntity.status(HttpStatus.GONE).body(ApiResponse.error("New URL has expired"));
                } else if (isLimitReached) {
                    return ResponseEntity.status(HttpStatus.GONE).body(ApiResponse.error("New URL usage limit reached"));
                } else {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error("Link is outside scheduled access window"));
                }
            }
        }

        // 4. Check Access Policy (PIN / Password Protection)
        Optional<AccessPolicy> accessPolicyOpt = accessPolicyRepository.findByShortUrlId(entity.getId());
        if (accessPolicyOpt.isPresent() && accessPolicyOpt.get().isPinOrPasswordProtected()) {
            AccessPolicy policy = accessPolicyOpt.get();

            // Check if already verified by cookie
            if (!isVerifiedByCookie(request, entity.getId())) {
                log.info("Prompting PIN/Password for url='{}' [IP={}]", fullUrl, ipAddress);
                return ResponseEntity.ok()
                        .contentType(MediaType.TEXT_HTML)
                        .body(linkUiRenderer.renderSecurityChallenge(path, policy.hasPin(), policy.hasPassword(), null));
            }
        }

        // 5. Public or Verified: Serve using cache service
        try {
            Optional<String> originalUrl = servingCacheService.resolveAndServe(entity.getNewUrl(), ipAddress, userAgent, referer);
            LinkMode mode = entity.getLinkMode() != null ? entity.getLinkMode() : LinkMode.REDIRECT;

            if (originalUrl.isPresent()) {
                String target = originalUrl.get();
                if (mode == LinkMode.PROXY) {
                    log.info("Proxying root fullUrl='{}' -> '{}' [IP={}]", fullUrl, target, ipAddress);
                    return proxyService.proxyRequest(target, request);
                }
                log.info("Redirecting root fullUrl='{}' -> '{}' [IP={}]", fullUrl, target, ipAddress);
                return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(target)).build();
            }

            if (mode == LinkMode.PROXY) {
                log.info("Proxying root fullUrl='{}' -> '{}' [IP={}]", fullUrl, entity.getOriginalUrl(), ipAddress);
                return proxyService.proxyRequest(entity.getOriginalUrl(), request);
            }
            return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(entity.getOriginalUrl())).build();
        } catch (UrlExpiredException e) {
            log.warn("New code expired: '{}' [IP={}]", path, ipAddress);
            if (isBrowserHtmlRequest(request)) {
                return ResponseEntity.status(HttpStatus.GONE)
                        .contentType(MediaType.TEXT_HTML)
                        .body(linkUiRenderer.renderExhaustedPage("Link Has Expired", "This short link has expired and is no longer accessible.", "Expired"));
            }
            return ResponseEntity.status(HttpStatus.GONE).body(ApiResponse.error("New URL has expired"));
        } catch (UrlUsageLimitExceededException e) {
            log.warn("New code usage limit exceeded: '{}' [IP={}]", path, ipAddress);
            if (isBrowserHtmlRequest(request)) {
                return ResponseEntity.status(HttpStatus.GONE)
                        .contentType(MediaType.TEXT_HTML)
                        .body(linkUiRenderer.renderExhaustedPage("Usage Limit Reached", "This short link has reached its maximum allowed number of accesses.", "Limit Reached"));
            }
            return ResponseEntity.status(HttpStatus.GONE).body(ApiResponse.error("New URL usage limit reached"));
        }
    }


    @Operation(
            summary = "Verify PIN or Password for secured link",
            description = "Validates submitted credentials. Redirects to target destination on success, returns challenge with error on failure."
    )
    @PostMapping("/**")
    public ResponseEntity<?> verifyAndServe(
            @RequestParam(required = false) String pin,
            @RequestParam(required = false) String password,
            HttpServletRequest request
    ) {
        String fullUrl = request.getRequestURL().toString();
        String path = "";
        if (request.getRequestURI().startsWith("/")) {
            path = request.getRequestURI().substring(1);
        }

        if (path.endsWith("/verify")) {
            path = path.substring(0, path.length() - "/verify".length());
            if (fullUrl.endsWith("/verify")) {
                fullUrl = fullUrl.substring(0, fullUrl.length() - "/verify".length());
            }
        }

        if (path.isBlank()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error("New URL not found"));
        }

        if (path.startsWith("api/")) {
            return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).build();
        }

        String ipAddress = extractClientIp(request);
        String userAgent = request.getHeader("User-Agent");
        String referer = request.getHeader("Referer");

        Optional<NewUrl> entityOpt = repository.findByNewUrl(fullUrl);
        if (entityOpt.isEmpty()) {
            entityOpt = repository.findByShortCode(path);
        }
        if (entityOpt.isEmpty()) {
            entityOpt = repository.findByCustomPath(path);
        }

        if (entityOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error("New URL not found"));
        }

        NewUrl entity = entityOpt.get();
        if (!entity.isActive()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error("New URL not found"));
        }

        // Check usage policy / expiration
        Optional<UsagePolicy> usagePolicyOpt = usagePolicyRepository.findByShortUrlId(entity.getId());
        boolean isExpired = (entity.getExpireAt() != null && Instant.now().isAfter(entity.getExpireAt()))
                || (usagePolicyOpt.isPresent() && usagePolicyOpt.get().isExpired());
        boolean isLimitReached = (entity.getUsageLimit() != null && entity.getClickCount() >= entity.getUsageLimit())
                || (usagePolicyOpt.isPresent() && usagePolicyOpt.get().isUsageLimitReached());
        boolean isOutsideSchedule = usagePolicyOpt.isPresent() && usagePolicyOpt.get().isOutsideSchedule();

        if (isExpired || isLimitReached || isOutsideSchedule) {
            String title = isExpired ? "Link Has Expired" : isLimitReached ? "Usage Limit Reached" : "Link Outside Schedule";
            String desc = isExpired
                    ? "This short link expired on " + entity.getExpireAt() + " and is no longer accessible."
                    : isLimitReached
                    ? "This short link has reached its maximum allowed number of accesses and is no longer accessible."
                    : "This short link is not currently accessible according to its access schedule.";
            String badge = isExpired ? "Expired" : isLimitReached ? "Limit Reached" : "Schedule Inactive";

            return ResponseEntity.status(HttpStatus.GONE)
                    .contentType(MediaType.TEXT_HTML)
                    .body(linkUiRenderer.renderExhaustedPage(title, desc, badge));
        }

        // Validate AccessPolicy credentials
        Optional<AccessPolicy> accessPolicyOpt = accessPolicyRepository.findByShortUrlId(entity.getId());
        if (accessPolicyOpt.isPresent() && accessPolicyOpt.get().isPinOrPasswordProtected()) {
            AccessPolicy policy = accessPolicyOpt.get();

            boolean pinValid = true;
            if (policy.hasPin()) {
                pinValid = pin != null && !pin.isBlank() && passwordEncoder.matches(pin.trim(), policy.getPinHash());
            }

            boolean passwordValid = true;
            if (policy.hasPassword()) {
                passwordValid = password != null && !password.isBlank() && passwordEncoder.matches(password, policy.getPasswordHash());
            }

            if (!pinValid || !passwordValid) {
                String errorMsg;
                if (policy.hasPin() && policy.hasPassword()) {
                    errorMsg = "Invalid PIN or Password. Please check your credentials and try again.";
                } else if (policy.hasPin()) {
                    errorMsg = "Invalid PIN. Please try again.";
                } else {
                    errorMsg = "Invalid Password. Please try again.";
                }

                log.warn("Failed security challenge attempt for url='{}' [IP={}]", fullUrl, ipAddress);
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .contentType(MediaType.TEXT_HTML)
                        .body(linkUiRenderer.renderSecurityChallenge(path, policy.hasPin(), policy.hasPassword(), errorMsg));
            }
        }

        // Credentials verified: record serve & increment usage
        log.info("Security challenge passed for url='{}' -> redirecting to '{}' [IP={}]",
                fullUrl, entity.getOriginalUrl(), ipAddress);
        eventPublisher.publishEvent(new ShortUrlServedEvent(entity.getId(), entity.getNewUrl(), ipAddress, userAgent, referer));

        ResponseCookie cookie = ResponseCookie.from("PREONS_SEC_" + entity.getId(), "VERIFIED")
                .path("/")
                .maxAge(600)
                .httpOnly(true)
                .build();

        URI redirectTarget = entity.getLinkMode() == LinkMode.PROXY
                ? URI.create(entity.getNewUrl())
                : URI.create(entity.getOriginalUrl());

        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .location(redirectTarget)
                .build();
    }

    private boolean isVerifiedByCookie(HttpServletRequest request, Long shortUrlId) {
        if (request.getCookies() == null) {
            return false;
        }
        String cookieName = "PREONS_SEC_" + shortUrlId;
        for (Cookie cookie : request.getCookies()) {
            if (cookieName.equals(cookie.getName()) && "VERIFIED".equals(cookie.getValue())) {
                return true;
            }
        }
        return false;
    }

    private boolean isBrowserHtmlRequest(HttpServletRequest request) {
        String accept = request.getHeader("Accept");
        return accept != null && accept.contains("text/html");
    }

    private String extractClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
