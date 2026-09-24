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
import com.preonsurl.apis.link.service.MirrorService;
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
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;


@Profile("serve")
@Controller
@Tag(name = "Serving", description = "Public endpoints for link resolution, redirection, and streaming proxy/mirror serving")
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
    private final MirrorService mirrorService;

    public ServingController(NewUrlServingCacheService servingCacheService,
                             NewUrlRepository repository,
                             AccessPolicyRepository accessPolicyRepository,
                             UsagePolicyRepository usagePolicyRepository,
                             LinkUiRenderer linkUiRenderer,
                             PasswordEncoder passwordEncoder,
                             ApplicationEventPublisher eventPublisher,
                             ProxyService proxyService,
                             MirrorService mirrorService) {
        this.servingCacheService = servingCacheService;
        this.repository = repository;
        this.accessPolicyRepository = accessPolicyRepository;
        this.usagePolicyRepository = usagePolicyRepository;
        this.linkUiRenderer = linkUiRenderer;
        this.passwordEncoder = passwordEncoder;
        this.eventPublisher = eventPublisher;
        this.proxyService = proxyService;
        this.mirrorService = mirrorService;
    }

    @Operation(summary = "Handle CORS Preflight", description = "Responds to preflight OPTIONS requests for short links and proxied/mirrored routes")
    @RequestMapping(value = "/**", method = RequestMethod.OPTIONS)
    public ResponseEntity<?> handlePreflight(HttpServletRequest request) {
        HttpHeaders headers = new HttpHeaders();
        String origin = request.getHeader("Origin");
        if (origin != null && !origin.isBlank()) {
            headers.set("Access-Control-Allow-Origin", origin);
            headers.set("Access-Control-Allow-Credentials", "true");
        } else {
            headers.set("Access-Control-Allow-Origin", "*");
        }
        headers.set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, PATCH, HEAD, OPTIONS");
        headers.set("Access-Control-Allow-Headers", request.getHeader("Access-Control-Request-Headers") != null
                ? request.getHeader("Access-Control-Request-Headers")
                : "*");
        headers.set("Access-Control-Allow-Credentials", "true");
        headers.set("Access-Control-Max-Age", "86400");
        return ResponseEntity.noContent().headers(headers).build();
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
                        return attachProxyContextCookie(proxyService.proxyRequest(target, request), path);
                    }
                    if (mode == LinkMode.MIRROR) {
                        log.info("Mirroring root fullUrl='{}' -> '{}' [IP={}]", fullUrl, target, ipAddress);
                        Optional<NewUrl> entityOpt = repository.findByNewUrl(fullUrl);
                        if (entityOpt.isEmpty()) entityOpt = repository.findByShortCode(path);
                        if (entityOpt.isEmpty()) entityOpt = repository.findByCustomPath(path);
                        if (entityOpt.isPresent()) {
                            return mirrorService.mirrorRequest(entityOpt.get().getShortCode() != null ? entityOpt.get().getShortCode() : path, "/", entityOpt.get(), request);
                        }
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
            // Check if this is a MIRROR subrequest: /{shortCode}/**
            Optional<MirrorRouteMatch> mirrorMatch = matchMirrorRoute(path);
            if (mirrorMatch.isPresent()) {
                MirrorRouteMatch match = mirrorMatch.get();
                NewUrl mirrorEntity = match.entity();
                if ("/".equals(match.subPath())) {
                    // Root URL with trailing slash: treat as root link visit
                    try {
                        Optional<String> orig = servingCacheService.resolveAndServe(mirrorEntity.getNewUrl(), ipAddress, userAgent, referer);
                        return mirrorService.mirrorRequest(match.prefix(), "/", mirrorEntity, request);
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
                } else {
                    // Subrequest: validate policies without incrementing user click count
                    ResponseEntity<?> policyViolation = validateMirrorSubrequestPolicies(mirrorEntity, request, match.prefix());
                    if (policyViolation != null) {
                        return policyViolation;
                    }
                    return mirrorService.mirrorRequest(match.prefix(), match.subPath(), mirrorEntity, request);
                }
            }

            // Check if this is an upstream proxy sub-resource or sub-path request
            Optional<ProxySubResourceTarget> proxyTargetOpt = resolveProxySubResource(request, path);
            if (proxyTargetOpt.isPresent()) {
                ProxySubResourceTarget proxyTarget = proxyTargetOpt.get();
                log.info("Proxying sub-resource path='{}' -> '{}' [IP={}]", path, proxyTarget.targetUrl(), ipAddress);
                return attachProxyContextCookie(
                        proxyService.proxyRequest(proxyTarget.targetUrl(), request, proxyTarget.upstreamReferer()),
                        proxyTarget.cookieIdentifier()
                );
            }

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
        boolean isExpired = (entity.getExpireAt() != null && Instant.now().isAfter(entity.getExpireAt())) || (usagePolicyOpt.isPresent() && usagePolicyOpt.get().isExpired());
        boolean isLimitReached = (entity.getUsageLimit() != null && entity.getClickCount() >= entity.getUsageLimit()) || (usagePolicyOpt.isPresent() && usagePolicyOpt.get().isUsageLimitReached());
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
            String cookieId = entity.getShortCode() != null ? entity.getShortCode() : path;

            if (originalUrl.isPresent()) {
                String target = originalUrl.get();
                if (mode == LinkMode.PROXY) {
                    log.info("Proxying root fullUrl='{}' -> '{}' [IP={}]", fullUrl, target, ipAddress);
                    return attachProxyContextCookie(proxyService.proxyRequest(target, request), cookieId);
                }
                if (mode == LinkMode.MIRROR) {
                    log.info("Mirroring root fullUrl='{}' -> '{}' [IP={}]", fullUrl, target, ipAddress);
                    return mirrorService.mirrorRequest(cookieId, "/", entity, request);
                }
                log.info("Redirecting root fullUrl='{}' -> '{}' [IP={}]", fullUrl, target, ipAddress);
                return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(target)).build();
            }

            if (mode == LinkMode.PROXY) {
                log.info("Proxying root fullUrl='{}' -> '{}' [IP={}]", fullUrl, entity.getOriginalUrl(), ipAddress);
                return attachProxyContextCookie(proxyService.proxyRequest(entity.getOriginalUrl(), request), cookieId);
            }
            if (mode == LinkMode.MIRROR) {
                log.info("Mirroring root fullUrl='{}' -> '{}' [IP={}]", fullUrl, entity.getOriginalUrl(), ipAddress);
                return mirrorService.mirrorRequest(cookieId, "/", entity, request);
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
            Optional<MirrorRouteMatch> mirrorMatch = matchMirrorRoute(path);
            if (mirrorMatch.isPresent()) {
                MirrorRouteMatch match = mirrorMatch.get();
                ResponseEntity<?> policyViolation = validateMirrorSubrequestPolicies(match.entity(), request, match.prefix());
                if (policyViolation != null) {
                    return policyViolation;
                }
                return mirrorService.mirrorRequest(match.prefix(), match.subPath(), match.entity(), request);
            }

            Optional<ProxySubResourceTarget> proxyTargetOpt = resolveProxySubResource(request, path);
            if (proxyTargetOpt.isPresent()) {
                ProxySubResourceTarget proxyTarget = proxyTargetOpt.get();
                log.info("Proxying sub-resource POST path='{}' -> '{}' [IP={}]", path, proxyTarget.targetUrl(), ipAddress);
                return attachProxyContextCookie(
                        proxyService.proxyRequest(proxyTarget.targetUrl(), request, proxyTarget.upstreamReferer()),
                        proxyTarget.cookieIdentifier()
                );
            }
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

        URI redirectTarget = (entity.getLinkMode() == LinkMode.PROXY || entity.getLinkMode() == LinkMode.MIRROR)
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

    record ProxySubResourceTarget(String targetUrl, String upstreamReferer, String cookieIdentifier) {}

    private Optional<ProxySubResourceTarget> resolveProxySubResource(HttpServletRequest request, String path) {
        // 1. Check if the path starts with a known PROXY short code or customPath (e.g. "code/sub/path")
        int firstSlash = path.indexOf('/');
        if (firstSlash > 0) {
            String prefix = path.substring(0, firstSlash);
            Optional<NewUrl> candidate = findProxyEntity(prefix);
            if (candidate.isPresent()) {
                String subPath = path.substring(firstSlash);
                return buildProxySubResourceTarget(candidate.get(), subPath, request, prefix);
            }
        }

        // 2. Check Referer header
        String referer = request.getHeader("Referer");
        if (referer != null && !referer.isBlank()) {
            try {
                URI refererUri = URI.create(referer);
                String refPath = refererUri.getPath();
                if (refPath != null) {
                    while (refPath.startsWith("/")) {
                        refPath = refPath.substring(1);
                    }
                    while (refPath.endsWith("/")) {
                        refPath = refPath.substring(0, refPath.length() - 1);
                    }
                    if (!refPath.isBlank()) {
                        Optional<NewUrl> candidate = findProxyEntity(refPath);
                        String identifier = refPath;
                        if (candidate.isEmpty() && refPath.contains("/")) {
                            identifier = refPath.substring(0, refPath.indexOf('/'));
                            candidate = findProxyEntity(identifier);
                        }
                        if (candidate.isPresent()) {
                            String subPath = request.getRequestURI();
                            return buildProxySubResourceTarget(candidate.get(), subPath, request, identifier);
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        }

        // 3. Check PREONS_PROXY_CTX Cookie
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if ("PREONS_PROXY_CTX".equals(cookie.getName())) {
                    String cookieVal = cookie.getValue();
                    if (cookieVal != null && !cookieVal.isBlank()) {
                        Optional<NewUrl> candidate = findProxyEntity(cookieVal);
                        if (candidate.isPresent()) {
                            String subPath = request.getRequestURI();
                            return buildProxySubResourceTarget(candidate.get(), subPath, request, cookieVal);
                        }
                    }
                }
            }
        }

        return Optional.empty();
    }

    private Optional<NewUrl> findProxyEntity(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            return Optional.empty();
        }
        Optional<NewUrl> entityOpt = repository.findByShortCode(identifier);
        if (entityOpt.isEmpty()) {
            entityOpt = repository.findByCustomPath(identifier);
        }
        if (entityOpt.isPresent()) {
            NewUrl entity = entityOpt.get();
            if (entity.isActive()) {
                if (entity.getExpireAt() == null || Instant.now().isBefore(entity.getExpireAt())) {
                    return Optional.of(entity);
                }
            }
        }
        return Optional.empty();
    }

    private Optional<ProxySubResourceTarget> buildProxySubResourceTarget(NewUrl entity, String subPath, HttpServletRequest request, String identifier) {
        try {
            URI baseUri = URI.create(entity.getOriginalUrl());
            String query = request.getQueryString();
            String pathAndQuery = subPath + (query != null && !query.isBlank() ? "?" + query : "");
            URI resolved = baseUri.resolve(pathAndQuery);
            return Optional.of(new ProxySubResourceTarget(resolved.toString(), entity.getOriginalUrl(), identifier));
        } catch (Exception e) {
            log.warn("Failed to resolve proxy sub-resource URI for base '{}' and subPath '{}': {}",
                    entity.getOriginalUrl(), subPath, e.getMessage());
            return Optional.empty();
        }
    }

    private ResponseEntity<?> attachProxyContextCookie(ResponseEntity<?> response, String identifier) {
        if (identifier == null || identifier.isBlank() || response == null) {
            return response;
        }
        ResponseCookie cookie = ResponseCookie.from("PREONS_PROXY_CTX", identifier)
                .path("/")
                .sameSite("Lax")
                .httpOnly(true)
                .build();
        HttpHeaders headers = new HttpHeaders();
        headers.addAll(response.getHeaders());
        headers.add(HttpHeaders.SET_COOKIE, cookie.toString());
        return ResponseEntity.status(response.getStatusCode())
                .headers(headers)
                .body(response.getBody());
    }

    record MirrorRouteMatch(NewUrl entity, String prefix, String subPath) {}

    private Optional<MirrorRouteMatch> matchMirrorRoute(String path) {
        if (path == null || path.isBlank()) {
            return Optional.empty();
        }
        int firstSlash = path.indexOf('/');
        if (firstSlash > 0) {
            String prefix = path.substring(0, firstSlash);
            Optional<NewUrl> candidate = findMirrorEntity(prefix);
            if (candidate.isPresent()) {
                String subPath = path.substring(firstSlash);
                return Optional.of(new MirrorRouteMatch(candidate.get(), prefix, subPath));
            }
        }
        return Optional.empty();
    }

    private Optional<NewUrl> findMirrorEntity(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            return Optional.empty();
        }
        Optional<NewUrl> entityOpt = repository.findByShortCode(identifier);
        if (entityOpt.isEmpty()) {
            entityOpt = repository.findByCustomPath(identifier);
        }
        if (entityOpt.isPresent()) {
            NewUrl entity = entityOpt.get();
            if (entity.isActive() && entity.getLinkMode() == LinkMode.MIRROR) {
                return Optional.of(entity);
            }
        }
        return Optional.empty();
    }

    private ResponseEntity<?> validateMirrorSubrequestPolicies(NewUrl entity, HttpServletRequest request, String path) {
        if (!entity.isActive()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error("New URL not found"));
        }

        Optional<UsagePolicy> usagePolicyOpt = usagePolicyRepository.findByShortUrlId(entity.getId());
        boolean isExpired = (entity.getExpireAt() != null && Instant.now().isAfter(entity.getExpireAt()))
                || (usagePolicyOpt.isPresent() && usagePolicyOpt.get().isExpired());
        boolean isLimitReached = (entity.getUsageLimit() != null && entity.getClickCount() >= entity.getUsageLimit())
                || (usagePolicyOpt.isPresent() && usagePolicyOpt.get().isUsageLimitReached());
        boolean isOutsideSchedule = usagePolicyOpt.isPresent() && usagePolicyOpt.get().isOutsideSchedule();

        if (isExpired || isLimitReached || isOutsideSchedule) {
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

        Optional<AccessPolicy> accessPolicyOpt = accessPolicyRepository.findByShortUrlId(entity.getId());
        if (accessPolicyOpt.isPresent() && accessPolicyOpt.get().isPinOrPasswordProtected()) {
            if (!isVerifiedByCookie(request, entity.getId())) {
                AccessPolicy policy = accessPolicyOpt.get();
                if (isBrowserHtmlRequest(request)) {
                    return ResponseEntity.ok()
                            .contentType(MediaType.TEXT_HTML)
                            .body(linkUiRenderer.renderSecurityChallenge(path, policy.hasPin(), policy.hasPassword(), null));
                } else {
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("PIN or password verification required"));
                }
            }
        }

        return null;
    }
}
