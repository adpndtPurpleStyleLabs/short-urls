package com.preonsurl.apis.link.service;

import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Locale;

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

        // Preserve / append query string if present and not already part of target
        if (queryString != null && !queryString.isBlank()) {
            String existingQuery = target.getRawQuery();
            String finalQuery;
            if (existingQuery == null || existingQuery.isBlank()) {
                finalQuery = queryString;
            } else if (!existingQuery.equals(queryString)) {
                finalQuery = existingQuery + "&" + queryString;
            } else {
                finalQuery = existingQuery;
            }

            try {
                target = new URI(
                        target.getScheme(),
                        target.getAuthority(),
                        target.getPath(),
                        finalQuery,
                        target.getFragment()
                );
            } catch (Exception e) {
                // Fallback string construction if standard constructor complains
                String baseStr = target.getScheme() + "://" + target.getAuthority() + target.getRawPath();
                target = URI.create(baseStr + "?" + finalQuery + (target.getRawFragment() != null ? "#" + target.getRawFragment() : ""));
            }
        }

        return target;
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
