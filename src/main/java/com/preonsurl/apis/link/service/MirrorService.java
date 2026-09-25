package com.preonsurl.apis.link.service;

import com.preonsurl.apis.link.dto.ApiResponse;
import com.preonsurl.apis.link.entity.NewUrl;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;

/**
 * Core service for MIRROR mode.
 * Fetches upstream resources, performs HTML and CSS URL rewriting through
 * the PreonsURL mirror namespace, rewrites redirect headers, and streams
 * non-rewritable assets.
 */
@Service
public class MirrorService {

    private static final Logger log = LoggerFactory.getLogger(MirrorService.class);

    public static final String DEFAULT_USER_AGENT = "PreonsURL-Mirror/1.0";
    public static final int MAX_REDIRECTS = 5;
    public static final int MAX_REWRITE_BODY_SIZE = 10 * 1024 * 1024; // 10 MB limit for buffering

    private static final Set<String> HOP_BY_HOP_HEADERS = Set.of(
            "connection",
            "keep-alive",
            "proxy-authenticate",
            "proxy-authorization",
            "te",
            "trailer",
            "trailers",
            "transfer-encoding",
            "upgrade"
    );

    private static final Set<String> FORWARDED_REQUEST_HEADERS = Set.of(
            "accept",
            "accept-language",
            "user-agent",
            "range",
            "if-range",
            "if-none-match",
            "if-modified-since",
            "cache-control",
            "content-type",
            "origin",
            "authorization",
            "x-requested-with"
    );

    private static final Set<String> FORWARDED_RESPONSE_HEADERS = Set.of(
            "content-type",
            "content-length",
            "content-disposition",
            "cache-control",
            "etag",
            "last-modified",
            "accept-ranges",
            "content-range",
            "content-encoding",
            "expires"
    );

    private final MirrorUrlResolver urlResolver;
    private final MirrorHtmlRewriter htmlRewriter;
    private final MirrorCssRewriter cssRewriter;
    private final MirrorCookieService cookieService;
    private final HttpClient httpClient;

    @org.springframework.beans.factory.annotation.Autowired
    public MirrorService(
            MirrorUrlResolver urlResolver,
            MirrorHtmlRewriter htmlRewriter,
            MirrorCssRewriter cssRewriter,
            MirrorCookieService cookieService
    ) {
        this(
                urlResolver,
                htmlRewriter,
                cssRewriter,
                cookieService,
                HttpClient.newBuilder()
                        .version(HttpClient.Version.HTTP_1_1)
                        .followRedirects(HttpClient.Redirect.NEVER)
                        .connectTimeout(Duration.ofSeconds(15))
                        .build()
        );
    }

    public MirrorService(
            MirrorUrlResolver urlResolver,
            MirrorHtmlRewriter htmlRewriter,
            MirrorCssRewriter cssRewriter,
            MirrorCookieService cookieService,
            HttpClient httpClient
    ) {
        this.urlResolver = urlResolver;
        this.htmlRewriter = htmlRewriter;
        this.cssRewriter = cssRewriter;
        this.cookieService = cookieService;
        this.httpClient = httpClient;
    }

    /**
     * Executes a mirror request for the given link and subpath.
     */
    public ResponseEntity<?> mirrorRequest(
            String shortCode,
            String mirrorPath,
            NewUrl entity,
            HttpServletRequest request
    ) {
        // Enforce supported HTTP methods
        String method = request.getMethod() != null ? request.getMethod().toUpperCase(Locale.ROOT) : "GET";
        if ("OPTIONS".equalsIgnoreCase(method)) {
            HttpHeaders corsHeaders = new HttpHeaders();
            applyCorsHeaders(corsHeaders, request);
            return ResponseEntity.noContent().headers(corsHeaders).build();
        }
        if (!Set.of("GET", "HEAD", "POST", "PUT", "DELETE", "PATCH").contains(method)) {
            return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                    .body(ApiResponse.error("Method '" + method + "' is not supported in MIRROR mode."));
        }

        Instant startTime = Instant.now();
        URI originalUri = URI.create(entity.getOriginalUrl());
        URI targetUri;

        String[] identifiers = entity != null
                ? new String[]{
                        shortCode,
                        entity.getShortCode(),
                        entity.getCustomPath(),
                        (entity.getCustomPath() != null && entity.getShortCode() != null ? entity.getCustomPath() + "/" + entity.getShortCode() : null)
                }
                : new String[]{shortCode};

        try {
            targetUri = urlResolver.resolveTargetUri(originalUri, mirrorPath, request.getQueryString(), identifiers);
            targetUri = ProxyResourceValidator.validateAndNormalizeUri(targetUri.toString());
        } catch (IllegalArgumentException e) {
            log.warn("Rejected mirror request for shortCode '{}', path '{}': {}", shortCode, mirrorPath, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("Invalid or unsafe destination URL: " + e.getMessage()));
        }

        try {
            return executeWithRedirects(shortCode, originalUri, targetUri, mirrorPath, request, startTime, identifiers);
        } catch (HttpTimeoutException e) {
            log.warn("Upstream mirror request timed out for shortCode '{}', host '{}'", shortCode, targetUri.getHost());
            return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT)
                    .body(ApiResponse.error("Upstream request timed out"));
        } catch (ConnectException | UnknownHostException e) {
            log.warn("Upstream connection failure for shortCode '{}', host '{}': {}", shortCode, targetUri.getHost(), e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(ApiResponse.error("Failed to connect to upstream server"));
        } catch (IllegalArgumentException e) {
            log.warn("Mirror validation failed for shortCode '{}', host '{}': {}", shortCode, targetUri.getHost(), e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("Invalid or unsafe destination URL: " + e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error in mirror request for shortCode '{}', host '{}'", shortCode, targetUri.getHost(), e);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(ApiResponse.error("Failed to process mirror request"));
        }
    }

    private ResponseEntity<?> executeWithRedirects(
            String shortCode,
            URI originalUri,
            URI initialTargetUri,
            String mirrorPath,
            HttpServletRequest clientRequest,
            Instant startTime,
            String[] identifiers
    ) throws Exception {
        URI currentUri = initialTargetUri;
        int redirectCount = 0;
        Set<URI> visitedUris = new HashSet<>();
        visitedUris.add(currentUri);

        while (true) {
            HttpRequest upstreamRequest = buildUpstreamRequest(currentUri, originalUri, clientRequest);
            HttpResponse<InputStream> upstreamResponse = httpClient.send(
                    upstreamRequest,
                    HttpResponse.BodyHandlers.ofInputStream()
            );

            int statusCode = upstreamResponse.statusCode();

            // Handle HTTP redirects (301, 302, 303, 307, 308)
            if (isRedirectStatus(statusCode)) {
                String locationHeader = upstreamResponse.headers().firstValue("Location").orElse(null);
                try {
                    upstreamResponse.body().close();
                } catch (Exception ignored) {
                }

                if (locationHeader == null || locationHeader.isBlank()) {
                    log.warn("Upstream mirror returned redirect {} without Location header from '{}'",
                            statusCode, sanitizeUriForLogging(currentUri));
                    return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                            .body(ApiResponse.error("Upstream returned redirect without Location header"));
                }

                redirectCount++;
                if (redirectCount > MAX_REDIRECTS) {
                    log.warn("Exceeded maximum redirects ({}) starting from '{}'", MAX_REDIRECTS, sanitizeUriForLogging(initialTargetUri));
                    return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                            .body(ApiResponse.error("Too many redirects from upstream server"));
                }

                URI nextUri;
                try {
                    nextUri = currentUri.resolve(locationHeader);
                    nextUri = ProxyResourceValidator.validateAndNormalizeUri(nextUri.toString());
                } catch (IllegalArgumentException e) {
                    log.warn("Redirect to unsafe destination '{}' blocked: {}", locationHeader, e.getMessage());
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                            .body(ApiResponse.error("Upstream redirected to an unsafe or invalid destination: " + e.getMessage()));
                }

                if (!visitedUris.add(nextUri)) {
                    log.warn("Redirect loop detected at '{}'", sanitizeUriForLogging(nextUri));
                    return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                            .body(ApiResponse.error("Redirect loop detected from upstream server"));
                }

                // If the redirect belongs to the allowed mirror origin, rewrite Location into a mirror URL
                if (urlResolver.isAllowedMirrorOrigin(originalUri, nextUri)) {
                    String mirrorLocation = urlResolver.toMirrorUrl(shortCode, originalUri, nextUri.toString());
                    log.info("Rewriting upstream redirect ({}) to mirror URL '{}'", statusCode, mirrorLocation);
                    HttpHeaders redirectHeaders = rewriteMirrorResponseHeaders(upstreamResponse, shortCode, false, clientRequest);
                    return ResponseEntity.status(statusCode)
                            .headers(redirectHeaders)
                            .location(URI.create(mirrorLocation))
                            .build();
                } else {
                    // External redirect: return untouched external location
                    HttpHeaders redirectHeaders = rewriteMirrorResponseHeaders(upstreamResponse, shortCode, false, clientRequest);
                    return ResponseEntity.status(statusCode)
                            .headers(redirectHeaders)
                            .location(nextUri)
                            .build();
                }
            }

            // Normal response (e.g. 200, 206, 404, etc.)
            String rawContentType = upstreamResponse.headers().firstValue("Content-Type").orElse("");
            String mimeType = rawContentType.split(";")[0].trim().toLowerCase(Locale.ROOT);
            String contentEncoding = upstreamResponse.headers().firstValue("Content-Encoding").orElse("").toLowerCase(Locale.ROOT);

            long durationMs = Duration.between(startTime, Instant.now()).toMillis();
            log.info("Mirror request for shortCode='{}', path='{}' -> '{}' completed with status {} in {} ms ({})",
                    shortCode, mirrorPath, sanitizeUriForLogging(currentUri), statusCode, durationMs, mimeType);

            // Handle HEAD requests
            if ("HEAD".equalsIgnoreCase(clientRequest.getMethod())) {
                try {
                    upstreamResponse.body().close();
                } catch (Exception ignored) {
                }
                HttpHeaders headers = rewriteMirrorResponseHeaders(upstreamResponse, shortCode, false, clientRequest);
                return ResponseEntity.status(statusCode).headers(headers).build();
            }

            // Determine if body should be rewritten
            if ("text/html".equals(mimeType)) {
                String html = readBodyAsString(upstreamResponse.body(), contentEncoding);
                String rewrittenHtml = htmlRewriter.rewrite(html, shortCode, currentUri, identifiers);
                byte[] bytes = rewrittenHtml.getBytes(StandardCharsets.UTF_8);

                HttpHeaders headers = rewriteMirrorResponseHeaders(upstreamResponse, shortCode, true, clientRequest);
                headers.setContentLength(bytes.length);
                headers.remove("Content-Encoding"); // body is now raw uncompressed UTF-8

                return ResponseEntity.status(statusCode)
                        .headers(headers)
                        .body(new ByteArrayResource(bytes));
            } else if ("text/css".equals(mimeType)) {
                String css = readBodyAsString(upstreamResponse.body(), contentEncoding);
                String rewrittenCss = cssRewriter.rewrite(css, shortCode, currentUri);
                byte[] bytes = rewrittenCss.getBytes(StandardCharsets.UTF_8);

                HttpHeaders headers = rewriteMirrorResponseHeaders(upstreamResponse, shortCode, true, clientRequest);
                headers.setContentLength(bytes.length);
                headers.remove("Content-Encoding"); // body is now raw uncompressed UTF-8

                return ResponseEntity.status(statusCode)
                        .headers(headers)
                        .body(new ByteArrayResource(bytes));
            } else {
                // Large/binary resources: stream directly without buffering
                HttpHeaders headers = rewriteMirrorResponseHeaders(upstreamResponse, shortCode, false, clientRequest);
                Resource resource = new InputStreamResource(upstreamResponse.body());
                return ResponseEntity.status(statusCode)
                        .headers(headers)
                        .body(resource);
            }
        }
    }

    private HttpRequest buildUpstreamRequest(URI uri, URI originalUri, HttpServletRequest clientRequest) {
        String method = clientRequest != null && clientRequest.getMethod() != null
                ? clientRequest.getMethod().toUpperCase(Locale.ROOT)
                : "GET";
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(60));

        if ("HEAD".equalsIgnoreCase(method)) {
            builder.method("HEAD", HttpRequest.BodyPublishers.noBody());
        } else if ("POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method)
                || "PATCH".equalsIgnoreCase(method) || "DELETE".equalsIgnoreCase(method)) {
            byte[] bodyBytes = readRequestBody(clientRequest);
            HttpRequest.BodyPublisher publisher = bodyBytes.length > 0
                    ? HttpRequest.BodyPublishers.ofByteArray(bodyBytes)
                    : HttpRequest.BodyPublishers.noBody();
            builder.method(method, publisher);
        } else {
            builder.GET();
        }

        forwardRequestHeaders(builder, clientRequest, originalUri);
        return builder.build();
    }

    private byte[] readRequestBody(HttpServletRequest request) {
        if (request == null) {
            return new byte[0];
        }
        try {
            return request.getInputStream().readAllBytes();
        } catch (Exception e) {
            log.warn("Failed to read client request body: {}", e.getMessage());
            return new byte[0];
        }
    }

    private void forwardRequestHeaders(HttpRequest.Builder builder, HttpServletRequest clientRequest, URI originalUri) {
        boolean userAgentSet = false;

        Enumeration<String> headerNames = clientRequest.getHeaderNames();
        if (headerNames != null) {
            while (headerNames.hasMoreElements()) {
                String name = headerNames.nextElement();
                String lower = name.toLowerCase(Locale.ROOT);

                if (FORWARDED_REQUEST_HEADERS.contains(lower)) {
                    String value = clientRequest.getHeader(name);
                    if (value != null && !value.isBlank()) {
                        builder.header(name, value);
                        if ("user-agent".equals(lower)) {
                            userAgentSet = true;
                        }
                    }
                }
            }
        }

        if (!userAgentSet) {
            builder.header("User-Agent", DEFAULT_USER_AGENT);
        }

        // Set upstream Referer to upstream origin/page to prevent hotlink blocking
        builder.header("Referer", originalUri.toString());

        // Prefer uncompressed responses for HTML/CSS so they can be parsed and rewritten without extra decompression
        builder.header("Accept-Encoding", "identity");
    }

    private HttpHeaders rewriteMirrorResponseHeaders(
            HttpResponse<?> upstreamResponse,
            String shortCode,
            boolean bodyRewritten,
            HttpServletRequest clientRequest
    ) {
        HttpHeaders headers = new HttpHeaders();
        applyCorsHeaders(headers, clientRequest);

        for (Map.Entry<String, List<String>> entry : upstreamResponse.headers().map().entrySet()) {
            String key = entry.getKey();
            if (key == null) {
                continue;
            }
            String lower = key.toLowerCase(Locale.ROOT);

            // Strip hop-by-hop headers
            if (HOP_BY_HOP_HEADERS.contains(lower)) {
                continue;
            }

            // Strip or sanitize CSP and X-Frame-Options that would break mirror rendering
            if ("content-security-policy".equals(lower) || "x-frame-options".equals(lower)) {
                continue;
            }

            // Transform Set-Cookie headers via MirrorCookieService
            if ("set-cookie".equals(lower)) {
                for (String cookieVal : entry.getValue()) {
                    String transformed = cookieService.transformSetCookie(cookieVal, shortCode);
                    if (transformed != null && !transformed.isBlank()) {
                        headers.add("Set-Cookie", transformed);
                    }
                }
                continue;
            }

            // If body was rewritten, Content-Length will be updated separately
            if (bodyRewritten && "content-length".equals(lower)) {
                continue;
            }

            if (FORWARDED_RESPONSE_HEADERS.contains(lower)) {
                for (String val : entry.getValue()) {
                    headers.add(key, val);
                }
            }
        }

        return headers;
    }

    private String readBodyAsString(InputStream inputStream, String contentEncoding) throws Exception {
        try (InputStream in = "gzip".equalsIgnoreCase(contentEncoding) ? new GZIPInputStream(inputStream) : inputStream;
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            byte[] buffer = new byte[8192];
            int read;
            int totalBytes = 0;

            while ((read = in.read(buffer)) != -1) {
                totalBytes += read;
                if (totalBytes > MAX_REWRITE_BODY_SIZE) {
                    throw new IllegalStateException("Response body exceeds maximum allowable rewrite size of " + MAX_REWRITE_BODY_SIZE + " bytes");
                }
                baos.write(buffer, 0, read);
            }

            return baos.toString(StandardCharsets.UTF_8);
        }
    }

    private boolean isRedirectStatus(int statusCode) {
        return statusCode == 301 || statusCode == 302 || statusCode == 303
                || statusCode == 307 || statusCode == 308;
    }

    private String sanitizeUriForLogging(URI uri) {
        if (uri == null) {
            return "null";
        }
        String path = uri.getPath() != null ? uri.getPath() : "";
        String host = uri.getHost() != null ? uri.getHost() : "";
        String scheme = uri.getScheme() != null ? uri.getScheme() : "http";
        int port = uri.getPort();
        String portPart = (port > 0 && port != 80 && port != 443) ? ":" + port : "";

        return scheme + "://" + host + portPart + path;
    }

    private void applyCorsHeaders(HttpHeaders headers, HttpServletRequest clientRequest) {
        String origin = clientRequest != null ? clientRequest.getHeader("Origin") : null;
        if (origin != null && !origin.isBlank()) {
            headers.set("Access-Control-Allow-Origin", origin);
            headers.set("Access-Control-Allow-Credentials", "true");
        } else {
            headers.set("Access-Control-Allow-Origin", "*");
        }
        headers.set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, PATCH, HEAD, OPTIONS");
        headers.set("Access-Control-Allow-Headers", "*");
        headers.set("Access-Control-Max-Age", "86400");
    }
}
