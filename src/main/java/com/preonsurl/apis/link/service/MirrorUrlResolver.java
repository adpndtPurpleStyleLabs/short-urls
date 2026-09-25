package com.preonsurl.apis.link.service;

import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Resolves target upstream URIs from mirror paths and converts upstream URIs
 * back into PreonsURL mirror URLs.
 */
@Component
public class MirrorUrlResolver {

    /**
     * Resolves the target upstream URI given the original destination URI and a mirror path.
     *
     * @param originalUri The configured original destination URI (e.g. https://www.ogaan.com/sale)
     * @param mirrorPath  The requested subpath (e.g. "/", "/pub/media/a.jpg", "api/products")
     * @param queryString Optional query string from the client request
     * @return The fully resolved upstream target URI
     */
    public URI resolveTargetUri(URI originalUri, String mirrorPath, String queryString) {
        return resolveTargetUri(originalUri, mirrorPath, queryString, null);
    }

    /**
     * Resolves the target upstream URI given the original destination URI, mirror path,
     * optional query string, and mirror shortCode to clean from query parameters.
     *
     * @param originalUri The configured original destination URI
     * @param mirrorPath  The requested subpath
     * @param queryString Optional query string from the client request
     * @param shortCode   The mirror shortCode or prefix to sanitize out of query parameters
     * @return The fully resolved upstream target URI
     */
    public URI resolveTargetUri(URI originalUri, String mirrorPath, String queryString, String... identifiers) {
        if (originalUri == null) {
            throw new IllegalArgumentException("originalUri cannot be null");
        }

        URI target;
        if (mirrorPath == null || mirrorPath.isBlank() || "/".equals(mirrorPath.trim())) {
            target = originalUri;
        } else {
            String sanitizedPath = mirrorPath.trim();
            // Check for path traversal sequences
            if (sanitizedPath.contains("../") || sanitizedPath.contains("/..") || sanitizedPath.equals("..")) {
                throw new IllegalArgumentException("Directory traversal in mirror path is not allowed: " + mirrorPath);
            }
            // Resolve using URI.resolve()
            if (sanitizedPath.startsWith("/")) {
                // Root-relative path: resolve against origin
                target = originalUri.resolve(sanitizedPath);
            } else {
                // Relative path: resolve relative to original URI
                target = originalUri.resolve(sanitizedPath);
            }
        }

        // Normalize target to resolve any '..' segments
        target = target.normalize();

        // Prevent mirror escape (must retain same scheme and host as originalUri)
        if (!isAllowedMirrorOrigin(originalUri, target)) {
            throw new IllegalArgumentException("Mirror path escapes allowed origin: " + target);
        }

        // Clean mirror identifiers/shortCode out of query parameters before forwarding to upstream
        String cleanedQuery = cleanQueryString(queryString, identifiers);

        // Preserve / append query string if present and not already part of target
        if (cleanedQuery != null && !cleanedQuery.isBlank()) {
            String existingQuery = target.getRawQuery();
            String finalQuery;
            if (existingQuery == null || existingQuery.isBlank()) {
                finalQuery = cleanedQuery;
            } else if (!existingQuery.equals(cleanedQuery)) {
                finalQuery = existingQuery + "&" + cleanedQuery;
            } else {
                finalQuery = existingQuery;
            }

            String safeQuery = finalQuery
                    .replace("{", "%7B")
                    .replace("}", "%7D")
                    .replace("\"", "%22")
                    .replace(" ", "%20");

            String path = target.getRawPath() != null ? target.getRawPath() : "";
            String baseStr = target.getScheme() + "://" + target.getAuthority() + path;
            target = URI.create(baseStr + "?" + safeQuery + (target.getRawFragment() != null ? "#" + target.getRawFragment() : ""));
        }

        return target;
    }

    /**
     * Sanitizes mirror shortCode prefix and identifiers out of query strings so upstream servers receive
     * clean query parameters (e.g. {"key":"home"} instead of {"key":"/28PDXdIMXrP"}).
     *
     * @param queryString Raw or URL-encoded query string from client request
     * @param identifiers Mirror shortCodes, custom paths, or combined prefixes to sanitize
     * @return The cleaned query string ready for upstream transmission
     */
    public String cleanQueryString(String queryString, String... identifiers) {
        if (queryString == null || queryString.isBlank() || identifiers == null || identifiers.length == 0) {
            return queryString;
        }

        java.util.Set<String> tokenSet = new java.util.LinkedHashSet<>();
        for (String id : identifiers) {
            if (id != null && !id.isBlank()) {
                String clean = id.trim();
                while (clean.startsWith("/")) clean = clean.substring(1);
                while (clean.endsWith("/")) clean = clean.substring(0, clean.length() - 1);
                if (!clean.isBlank()) {
                    tokenSet.add(clean);
                    if (clean.contains("/")) {
                        for (String part : clean.split("/")) {
                            String p = part.trim();
                            if (!p.isBlank()) {
                                tokenSet.add(p);
                            }
                        }
                    }
                }
            }
        }

        if (tokenSet.isEmpty()) {
            return queryString;
        }

        java.util.List<String> sortedTokens = tokenSet.stream()
                .sorted((a, b) -> Integer.compare(b.length(), a.length()))
                .toList();

        String cleaned = queryString;

        for (String sc : sortedTokens) {
            String quoted = Pattern.quote(sc);

            // 1. JSON key alone equals token (root page) -> map to "home"
            // Plain: "key":"/token" or "key":"token" or "key":"/token/"
            cleaned = cleaned.replaceAll("(\"key\"\\s*:\\s*\")/?" + quoted + "/?(\")", "$1home$2");
            // URL-encoded: %22key%22%3A%22%2Ftoken%22
            cleaned = cleaned.replaceAll("(?i)(%22key%22\\s*(?::|%3A)\\s*(?:%22|\"))(?:/|%2F)?" + quoted + "(?:/|%2F)?((?:%22|\"))", "$1home$2");

            // 2. JSON key with subpath: "key":"/token/path" -> "key":"/path"
            cleaned = cleaned.replaceAll("(\"key\"\\s*:\\s*\")/?" + quoted + "/", "$1/");
            cleaned = cleaned.replaceAll("(?i)(%22key%22\\s*(?::|%3A)\\s*(?:%22|\"))(?:/|%2F)?" + quoted + "(%2F|/)", "$1$2");

            // 3. Any /token/ in query string -> /
            cleaned = cleaned.replace("/" + sc + "/", "/");
            cleaned = cleaned.replaceAll("(?i)%2F" + quoted + "%2F", "%2F");
            cleaned = cleaned.replaceAll("(?i)%2F" + quoted + "/", "/");
            cleaned = cleaned.replaceAll("(?i)/" + quoted + "%2F", "%2F");

            // 4. Any standalone /token at end of value (before &, ", ', }, %, or end of string)
            cleaned = cleaned.replaceAll("/" + quoted + "(?=[&\"'}\\]]|%22|%27|%7D|$)", "/");
            cleaned = cleaned.replaceAll("(?i)%2F" + quoted + "(?=[&\"'}\\]]|%22|%27|%7D|$)", "%2F");
        }

        // 5. If key was left as root "/" or empty "", map to "home"
        cleaned = cleaned.replaceAll("(\"key\"\\s*:\\s*\")/+(\")", "$1home$2");
        cleaned = cleaned.replaceAll("(?i)(%22key%22\\s*(?::|%3A)\\s*(?:%22|\"))(?:/|%2F)+((?:%22|\"))", "$1home$2");

        return cleaned;
    }

    /**
     * Determines whether the requested URI belongs to the allowed mirror origin.
     * Default rule: same scheme and same host.
     */
    public boolean isAllowedMirrorOrigin(URI originalOrigin, URI requestedUri) {
        if (originalOrigin == null || requestedUri == null) {
            return false;
        }

        String origScheme = originalOrigin.getScheme();
        String reqScheme = requestedUri.getScheme();
        if (origScheme != null && reqScheme != null) {
            if (!origScheme.equalsIgnoreCase(reqScheme)) {
                return false;
            }
        }

        String origHost = originalOrigin.getHost();
        String reqHost = requestedUri.getHost();
        if (origHost == null || reqHost == null) {
            // Relative URI with no host belongs to the same origin
            return true;
        }

        if (!origHost.equalsIgnoreCase(reqHost)) {
            return false;
        }

        int origPort = effectivePort(origScheme, originalOrigin.getPort());
        int reqPort = effectivePort(reqScheme, requestedUri.getPort());
        return origPort == reqPort;
    }

    /**
     * Converts an upstream URI to a PreonsURL mirror URL if it belongs to the allowed mirror origin.
     *
     * @param shortCode    The short code or custom path identifying the mirror link
     * @param currentDocUri The current upstream document URI for resolving relative links
     * @param targetUriStr  The URI string to convert
     * @return The rewritten mirror URL string, or the original URI string if external
     */
    public String toMirrorUrl(String shortCode, URI currentDocUri, String targetUriStr) {
        if (targetUriStr == null || targetUriStr.isBlank()) {
            return targetUriStr;
        }

        String trimmed = targetUriStr.trim();

        // Skip non-HTTP schemes and anchors
        if (trimmed.startsWith("#") ||
                trimmed.toLowerCase(Locale.ROOT).startsWith("javascript:") ||
                trimmed.toLowerCase(Locale.ROOT).startsWith("mailto:") ||
                trimmed.toLowerCase(Locale.ROOT).startsWith("tel:") ||
                trimmed.toLowerCase(Locale.ROOT).startsWith("data:")) {
            return targetUriStr;
        }

        // Handle protocol-relative URLs (//example.com/asset.jpg)
        if (trimmed.startsWith("//")) {
            String scheme = currentDocUri.getScheme() != null ? currentDocUri.getScheme() : "https";
            trimmed = scheme + ":" + trimmed;
        }

        URI resolvedTarget;
        try {
            resolvedTarget = currentDocUri.resolve(trimmed);
        } catch (Exception e) {
            return targetUriStr;
        }

        if (isAllowedMirrorOrigin(currentDocUri, resolvedTarget)) {
            String prefix = shortCode.startsWith("/") ? shortCode : "/" + shortCode;
            while (prefix.endsWith("/")) {
                prefix = prefix.substring(0, prefix.length() - 1);
            }

            String path = resolvedTarget.getRawPath();
            if (path == null || path.isBlank()) {
                path = "/";
            }
            if (!path.startsWith("/")) {
                path = "/" + path;
            }

            StringBuilder sb = new StringBuilder();
            sb.append(prefix).append(path);
            if (resolvedTarget.getRawQuery() != null && !resolvedTarget.getRawQuery().isBlank()) {
                sb.append("?").append(resolvedTarget.getRawQuery());
            }
            if (resolvedTarget.getRawFragment() != null && !resolvedTarget.getRawFragment().isBlank()) {
                sb.append("#").append(resolvedTarget.getRawFragment());
            }
            return sb.toString();
        }

        // External domain: leave unchanged
        return targetUriStr;
    }

    private int effectivePort(String scheme, int port) {
        if (port > 0) {
            return port;
        }
        if ("https".equalsIgnoreCase(scheme)) {
            return 443;
        }
        return 80;
    }
}
