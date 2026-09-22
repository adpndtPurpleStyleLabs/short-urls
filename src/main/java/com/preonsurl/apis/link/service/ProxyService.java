package com.preonsurl.apis.link.service;

import com.preonsurl.apis.link.dto.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.time.Instant;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Generic streaming reverse proxy service.
 * Proxies HTTP/HTTPS requests to upstream targets with SSRF validation,
 * manual redirect verification, request header allowlisting, range support,
 * and streaming response delivery.
 */
@Service
public class ProxyService {

    private static final Logger log = LoggerFactory.getLogger(ProxyService.class);

    public static final String DEFAULT_USER_AGENT = "PreonsURL-Proxy/1.0";
    public static final int MAX_REDIRECTS = 5;

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
            "content-type"
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

    private final HttpClient httpClient;

    public ProxyService() {
        this(HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofSeconds(15))
                .build());
    }

    public ProxyService(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    /**
     * Proxies the client request to the specified target URL.
     */
    public ResponseEntity<?> proxyRequest(String targetUrl, HttpServletRequest clientRequest) {
        return proxyRequest(targetUrl, clientRequest, null);
    }

    /**
     * Proxies the client request to the specified target URL with an optional upstream Referer header.
     */
    public ResponseEntity<?> proxyRequest(String targetUrl, HttpServletRequest clientRequest, String upstreamReferer) {
        Instant startTime = Instant.now();
        URI initialUri;
        try {
            initialUri = ProxyResourceValidator.validateAndNormalizeUri(targetUrl);
        } catch (IllegalArgumentException e) {
            log.warn("Rejected proxy request to invalid or unsafe URL '{}': {}", targetUrl, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("Invalid or unsafe destination URL: " + e.getMessage()));
        }

        try {
            return executeWithRedirects(initialUri, clientRequest, startTime, upstreamReferer);
        } catch (HttpTimeoutException e) {
            log.warn("Upstream request timed out for target host '{}'", initialUri.getHost());
            return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT)
                    .body(ApiResponse.error("Upstream request timed out"));
        } catch (ConnectException | UnknownHostException e) {
            log.warn("Upstream connection failure for target host '{}': {}", initialUri.getHost(), e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(ApiResponse.error("Failed to connect to upstream server"));
        } catch (IllegalArgumentException e) {
            log.warn("Proxy validation failed during execution for target host '{}': {}", initialUri.getHost(), e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("Invalid or unsafe destination URL: " + e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error proxying to target host '{}'", initialUri.getHost(), e);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(ApiResponse.error("Failed to proxy upstream resource"));
        }
    }

    private ResponseEntity<?> executeWithRedirects(URI initialUri, HttpServletRequest clientRequest, Instant startTime, String upstreamReferer)
            throws Exception {
        URI currentUri = initialUri;
        int redirectCount = 0;
        Set<URI> visitedUris = new HashSet<>();
        visitedUris.add(currentUri);

        while (true) {
            HttpRequest upstreamRequest = buildUpstreamRequest(currentUri, clientRequest, upstreamReferer);
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
                    log.warn("Received redirect status {} without Location header from '{}'", statusCode, sanitizeUriForLogging(currentUri));
                    return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                            .body(ApiResponse.error("Upstream returned redirect without Location header"));
                }

                redirectCount++;
                if (redirectCount > MAX_REDIRECTS) {
                    log.warn("Exceeded maximum redirects ({}) starting from '{}'", MAX_REDIRECTS, sanitizeUriForLogging(initialUri));
                    return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                            .body(ApiResponse.error("Too many redirects from upstream server"));
                }

                URI nextUri;
                try {
                    nextUri = currentUri.resolve(locationHeader);
                    nextUri = ProxyResourceValidator.validateAndNormalizeUri(nextUri.toString());
                } catch (IllegalArgumentException e) {
                    log.warn("Redirect to unsafe or invalid destination '{}' blocked by SSRF validator: {}", locationHeader, e.getMessage());
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                            .body(ApiResponse.error("Upstream redirected to an unsafe or invalid destination: " + e.getMessage()));
                }

                if (!visitedUris.add(nextUri)) {
                    log.warn("Redirect loop detected at '{}'", sanitizeUriForLogging(nextUri));
                    return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                            .body(ApiResponse.error("Redirect loop detected from upstream server"));
                }

                log.info("Following safe upstream redirect ({}/{}) from '{}' to '{}'",
                        redirectCount, MAX_REDIRECTS, sanitizeUriForLogging(currentUri), sanitizeUriForLogging(nextUri));
                currentUri = nextUri;
                continue;
            }

            // Normal or terminal response: copy headers and stream body
            HttpHeaders responseHeaders = copyResponseHeaders(upstreamResponse);
            Resource resource = new InputStreamResource(upstreamResponse.body());

            long durationMs = Duration.between(startTime, Instant.now()).toMillis();
            long contentLength = upstreamResponse.headers().firstValueAsLong("Content-Length").orElse(-1L);
            log.info("Proxied upstream request to '{}' completed with status {} in {} ms (content-length: {})",
                    sanitizeUriForLogging(currentUri), statusCode, durationMs, contentLength >= 0 ? contentLength : "unknown");

            return ResponseEntity.status(statusCode)
                    .headers(responseHeaders)
                    .body(resource);
        }
    }

    private HttpRequest buildUpstreamRequest(URI uri, HttpServletRequest clientRequest, String upstreamReferer) {
        String method = clientRequest != null ? clientRequest.getMethod() : "GET";
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(60));

        if ("HEAD".equalsIgnoreCase(method)) {
            requestBuilder.method("HEAD", HttpRequest.BodyPublishers.noBody());
        } else if ("POST".equalsIgnoreCase(method)) {
            try {
                byte[] bodyBytes = clientRequest.getInputStream().readAllBytes();
                requestBuilder.POST(HttpRequest.BodyPublishers.ofByteArray(bodyBytes));
            } catch (Exception e) {
                requestBuilder.POST(HttpRequest.BodyPublishers.noBody());
            }
        } else {
            requestBuilder.GET();
        }

        forwardRequestHeaders(requestBuilder, clientRequest, upstreamReferer);
        return requestBuilder.build();
    }

    private void forwardRequestHeaders(HttpRequest.Builder builder, HttpServletRequest clientRequest, String upstreamReferer) {
        boolean userAgentSet = false;

        if (clientRequest != null) {
            Enumeration<String> headerNames = clientRequest.getHeaderNames();
            if (headerNames != null) {
                while (headerNames.hasMoreElements()) {
                    String name = headerNames.nextElement();
                    String lowerName = name.toLowerCase(Locale.ROOT);

                    if (FORWARDED_REQUEST_HEADERS.contains(lowerName)) {
                        String value = clientRequest.getHeader(name);
                        if (value != null && !value.isBlank()) {
                            builder.header(name, value);
                            if ("user-agent".equals(lowerName)) {
                                userAgentSet = true;
                            }
                        }
                    }
                }
            }
        }

        // Default User-Agent if none provided by client
        if (!userAgentSet) {
            builder.header("User-Agent", DEFAULT_USER_AGENT);
        }

        // Set upstream referer if provided (prevents 403 hotlinking issues for proxied sub-resources)
        if (upstreamReferer != null && !upstreamReferer.isBlank()) {
            builder.header("Referer", upstreamReferer);
        }
    }

    private HttpHeaders copyResponseHeaders(HttpResponse<?> upstreamResponse) {
        HttpHeaders headers = new HttpHeaders();
        upstreamResponse.headers().map().forEach((key, values) -> {
            if (key != null) {
                String lower = key.toLowerCase(Locale.ROOT);
                if (!isHopByHopHeader(lower) && FORWARDED_RESPONSE_HEADERS.contains(lower)) {
                    for (String val : values) {
                        headers.add(key, val);
                    }
                }
            }
        });
        return headers;
    }

    private boolean isHopByHopHeader(String headerName) {
        return HOP_BY_HOP_HEADERS.contains(headerName.toLowerCase(Locale.ROOT));
    }

    private boolean isRedirectStatus(int statusCode) {
        return statusCode == 301 || statusCode == 302 || statusCode == 303
                || statusCode == 307 || statusCode == 308;
    }

    private String sanitizeUriForLogging(URI uri) {
        if (uri == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        if (uri.getScheme() != null) {
            sb.append(uri.getScheme()).append("://");
        }
        if (uri.getHost() != null) {
            sb.append(uri.getHost());
        }
        if (uri.getPort() != -1 && uri.getPort() != 80 && uri.getPort() != 443) {
            sb.append(":").append(uri.getPort());
        }
        if (uri.getPath() != null) {
            sb.append(uri.getPath());
        }
        if (uri.getQuery() != null && !uri.getQuery().isBlank()) {
            sb.append("?[REDACTED]");
        }
        return sb.toString();
    }
}
