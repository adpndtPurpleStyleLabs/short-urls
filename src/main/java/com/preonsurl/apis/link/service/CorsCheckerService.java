package com.preonsurl.apis.link.service;

import com.preonsurl.apis.link.dto.CorsCheckResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

@Service
public class CorsCheckerService  {

    private static final Logger log = LoggerFactory.getLogger(CorsCheckerService.class);

    private final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public CorsCheckResult check(String endpoint, String origin, String requestedMethod, List<String> requestedHeaders) {
        if (endpoint == null || endpoint.isBlank()) {
            return new CorsCheckResult(0, false, false, false, null, null, null, "Destination URL is required");
        }

        String normalizedEndpoint = endpoint.trim();
        if (!normalizedEndpoint.startsWith("http://") && !normalizedEndpoint.startsWith("https://")) {
            normalizedEndpoint = "https://" + normalizedEndpoint;
        }

        String normalizedOrigin = normalizeOrigin(origin);
        String method = (requestedMethod != null && !requestedMethod.isBlank()) ? requestedMethod.trim().toUpperCase() : "GET";
        List<String> headers = (requestedHeaders != null) ? requestedHeaders : List.of();

        try {
            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(normalizedEndpoint))
                    .timeout(Duration.ofSeconds(6))
                    .header("Origin", normalizedOrigin)
                    .header("Access-Control-Request-Method", method)
                    .method("OPTIONS", HttpRequest.BodyPublishers.noBody());

            if (!headers.isEmpty()) {
                reqBuilder.header("Access-Control-Request-Headers", String.join(",", headers));
            }

            HttpResponse<Void> response = httpClient.send(
                    reqBuilder.build(),
                    HttpResponse.BodyHandlers.discarding()
            );

            String allowOrigin = response.headers()
                    .firstValue("Access-Control-Allow-Origin")
                    .orElse(null);

            String allowMethods = response.headers()
                    .firstValue("Access-Control-Allow-Methods")
                    .orElse(null);

            String allowHeaders = response.headers()
                    .firstValue("Access-Control-Allow-Headers")
                    .orElse(null);

            boolean originAllowed = isOriginAllowed(normalizedOrigin, allowOrigin);
            boolean methodAllowed = isMethodAllowed(method, allowMethods);
            boolean headersAllowed = areHeadersAllowed(headers, allowHeaders);

            String message;
            if (originAllowed && methodAllowed && headersAllowed && response.statusCode() >= 200 && response.statusCode() < 300) {
                message = "CORS preflight successful. Cross-origin access allowed.";
            } else if (!originAllowed) {
                message = allowOrigin == null
                        ? "Missing Access-Control-Allow-Origin header in response."
                        : "Origin '" + normalizedOrigin + "' is not permitted (Target allows: '" + allowOrigin + "').";
            } else if (response.statusCode() < 200 || response.statusCode() >= 300) {
                message = "Target responded with HTTP status " + response.statusCode();
            } else {
                message = "CORS restriction detected.";
            }

            return new CorsCheckResult(
                    response.statusCode(),
                    originAllowed,
                    methodAllowed,
                    headersAllowed,
                    allowOrigin,
                    allowMethods,
                    allowHeaders,
                    message
            );

        } catch (Exception e) {
            log.warn("CORS check failed for endpoint '{}' and origin '{}': {}", normalizedEndpoint, normalizedOrigin, e.getMessage());
            return new CorsCheckResult(
                    0,
                    false,
                    false,
                    false,
                    null,
                    null,
                    null,
                    "Unable to reach destination server: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName())
            );
        }
    }

    private String normalizeOrigin(String origin) {
        if (origin == null || origin.isBlank()) {
            return "https://preonsurl.com";
        }
        String trimmed = origin.trim();
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return stripTrailingSlash(trimmed);
        }
        if (trimmed.contains("localhost") || trimmed.startsWith("127.0.0.1")) {
            return "http://" + stripTrailingSlash(trimmed);
        }
        return "https://" + stripTrailingSlash(trimmed);
    }

    private String stripTrailingSlash(String str) {
        return str.replaceAll("/+$", "");
    }

    private boolean isOriginAllowed(String requestedOrigin, String allowOrigin) {
        if (allowOrigin == null || allowOrigin.isBlank()) {
            return false;
        }

        String trimmedAllow = allowOrigin.trim();
        if ("*".equals(trimmedAllow)) {
            return true;
        }

        String cleanRequested = stripTrailingSlash(requestedOrigin);
        String cleanAllowed = stripTrailingSlash(trimmedAllow);

        if (cleanRequested.equalsIgnoreCase(cleanAllowed)) {
            return true;
        }

        // Handle comma-separated list of origins if returned
        if (trimmedAllow.contains(",")) {
            for (String part : trimmedAllow.split(",")) {
                if (cleanRequested.equalsIgnoreCase(stripTrailingSlash(part.trim()))) {
                    return true;
                }
            }
        }

        return false;
    }

    private boolean isMethodAllowed(String requestedMethod, String allowMethods) {
        if (allowMethods == null) {
            // Some preflight responses omit Allow-Methods for standard GET requests if Origin is allowed
            return "GET".equalsIgnoreCase(requestedMethod);
        }

        String trimmed = allowMethods.trim();
        if ("*".equals(trimmed)) {
            return true;
        }

        return List.of(trimmed.split(","))
                .stream()
                .map(String::trim)
                .anyMatch(method ->
                        method.equalsIgnoreCase(requestedMethod) || "*".equals(method)
                );
    }

    private boolean areHeadersAllowed(List<String> requestedHeaders, String allowHeaders) {
        if (requestedHeaders == null || requestedHeaders.isEmpty()) {
            return true;
        }

        if (allowHeaders == null) {
            return false;
        }

        String trimmed = allowHeaders.trim();
        if ("*".equals(trimmed)) {
            return true;
        }

        List<String> allowed = List.of(trimmed.toLowerCase().split(","));

        return requestedHeaders.stream()
                .map(String::toLowerCase)
                .allMatch(header ->
                        allowed.stream()
                                .map(String::trim)
                                .anyMatch(h -> h.equals(header) || "*".equals(h))
                );
    }
}
