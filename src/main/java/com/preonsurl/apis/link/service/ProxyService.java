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

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Enumeration;
import java.util.Locale;
import java.util.Set;

@Service
public class ProxyService {

    private static final Logger log = LoggerFactory.getLogger(ProxyService.class);

    public static final String DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36";

    private static final Set<String> HOP_BY_HOP_HEADERS = Set.of(
            "connection",
            "keep-alive",
            "proxy-authenticate",
            "proxy-authorization",
            "te",
            "trailers",
            "transfer-encoding",
            "upgrade"
    );

    private static final Set<String> SAFE_FORWARD_HEADERS = Set.of(
            "range",
            "if-range",
            "if-modified-since",
            "if-none-match",
            "accept-language"
    );

    private static final Set<String> SUSPECT_USER_AGENTS = Set.of(
            "java", "curl", "wget", "postman", "python", "go-http", "apache-httpclient"
    );

    private final HttpClient httpClient;

    public ProxyService() {
        this(HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .connectTimeout(Duration.ofSeconds(15))
                .build());
    }

    public ProxyService(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public ResponseEntity<?> proxyRequest(String targetUrl, HttpServletRequest clientRequest) {
        try {
            URI targetUri = URI.create(targetUrl);
            HttpResponse<InputStream> upstreamResponse = executeRequest(targetUri, clientRequest, false);

            int statusCode = upstreamResponse.statusCode();

            // If upstream returned 403 Forbidden, retry once with simulated browser origin headers
            if (statusCode == 403) {
                log.warn("Upstream URL returned 403 for '{}', retrying with browser origin spoofing headers...", targetUrl);
                try {
                    upstreamResponse.body().close();
                } catch (Exception ignored) {
                }
                upstreamResponse = executeRequest(targetUri, clientRequest, true);
                statusCode = upstreamResponse.statusCode();
            }

            HttpHeaders responseHeaders = new HttpHeaders();
            upstreamResponse.headers().map().forEach((key, values) -> {
                if (key != null && !HOP_BY_HOP_HEADERS.contains(key.toLowerCase(Locale.ROOT))) {
                    for (String val : values) {
                        responseHeaders.add(key, val);
                    }
                }
            });

            Resource resource = new InputStreamResource(upstreamResponse.body());
            return ResponseEntity.status(statusCode)
                    .headers(responseHeaders)
                    .body(resource);

        } catch (Exception e) {
            log.error("Failed to proxy upstream URL: '{}' - error: {}", targetUrl, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(ApiResponse.error("Failed to proxy upstream resource: " + e.getMessage()));
        }
    }

    private HttpResponse<InputStream> executeRequest(URI targetUri, HttpServletRequest clientRequest, boolean forceBrowserHeaders)
            throws Exception {
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(targetUri)
                .timeout(Duration.ofSeconds(60))
                .GET();

        // 1. Resolve User-Agent: ensure it's never empty and never a bot / Java-http-client
        String userAgent = null;
        if (!forceBrowserHeaders && clientRequest != null) {
            userAgent = clientRequest.getHeader("User-Agent");
        }
        if (userAgent == null || userAgent.isBlank() || isSuspectUserAgent(userAgent)) {
            userAgent = DEFAULT_USER_AGENT;
        }
        requestBuilder.header("User-Agent", userAgent);

        // 2. Accept header:
        // When client navigates in browser, Accept may be 'text/html...'. For resources, request '*/*'
        String accept = null;
        if (!forceBrowserHeaders && clientRequest != null) {
            accept = clientRequest.getHeader("Accept");
        }
        if (accept == null || accept.isBlank() || accept.startsWith("text/html")) {
            accept = "*/*";
        }
        requestBuilder.header("Accept", accept);

        // 3. Referer & Origin: set to the target's origin to bypass CDN hotlink protection
        String origin = targetUri.getScheme() + "://" + targetUri.getHost();
        if (targetUri.getPort() > 0 && targetUri.getPort() != 80 && targetUri.getPort() != 443) {
            origin += ":" + targetUri.getPort();
        }
        requestBuilder.header("Referer", origin + "/");
        requestBuilder.header("Origin", origin);

        // 4. Standard browser navigation headers
        requestBuilder.header("Accept-Language", "en-US,en;q=0.9");
        requestBuilder.header("Sec-Fetch-Dest", "empty");
        requestBuilder.header("Sec-Fetch-Mode", "cors");
        requestBuilder.header("Sec-Fetch-Site", "same-origin");

        // 5. Forward safe headers like Range (for chunked / resume downloads)
        if (clientRequest != null) {
            Enumeration<String> headerNames = clientRequest.getHeaderNames();
            if (headerNames != null) {
                while (headerNames.hasMoreElements()) {
                    String name = headerNames.nextElement();
                    String lowerName = name.toLowerCase(Locale.ROOT);
                    if (SAFE_FORWARD_HEADERS.contains(lowerName)) {
                        String value = clientRequest.getHeader(name);
                        if (value != null && !value.isBlank()) {
                            requestBuilder.header(name, value);
                        }
                    }
                }
            }
        }

        HttpRequest upstreamRequest = requestBuilder.build();
        return httpClient.send(upstreamRequest, HttpResponse.BodyHandlers.ofInputStream());
    }

    private boolean isSuspectUserAgent(String userAgent) {
        String lower = userAgent.toLowerCase(Locale.ROOT);
        return SUSPECT_USER_AGENTS.stream().anyMatch(lower::contains);
    }
}
