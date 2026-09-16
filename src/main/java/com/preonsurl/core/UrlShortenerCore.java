package com.preonsurl.core;

import java.net.URI;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Core URL Shortening service facade.
 * Validates original target URLs and constructs shortened URLs with the configured domain.
 */
public final class UrlShortenerCore {

    private final ShortCodePool codePool;
    private final String domain;

    /**
     * Initializes UrlShortenerCore.
     *
     * @param codePool pre-buffered code pool
     * @param domain   base URL domain (e.g. "https://short.my")
     */
    public UrlShortenerCore(ShortCodePool codePool, String domain) {
        this.codePool = Objects.requireNonNull(codePool, "ShortCodePool must not be null");
        Objects.requireNonNull(domain, "Domain must not be null");
        if (domain.isBlank()) {
            throw new IllegalArgumentException("Domain must not be blank");
        }
        this.domain = domain.endsWith("/") ? domain.substring(0, domain.length() - 1) : domain;
    }

    /**
     * Shortens a target URL using a default 5-second permit timeout.
     *
     * @param originalUrl destination URL (http/https)
     * @return full shortened URL (e.g. "https://short.my/k9X2b")
     * @throws InterruptedException if interrupted while waiting for code permit
     * @throws TimeoutException     if timed out waiting for code permit
     */
    public String shorten(String originalUrl) throws InterruptedException, TimeoutException {
        return shorten(originalUrl, 5, TimeUnit.SECONDS);
    }

    /**
     * Shortens a target URL with a custom timeout.
     *
     * @param originalUrl destination URL (http/https)
     * @param timeout     max wait time
     * @param unit        time unit
     * @return full shortened URL
     * @throws InterruptedException if interrupted while waiting for code permit
     * @throws TimeoutException     if timed out waiting for code permit
     */
    public String shorten(String originalUrl, long timeout, TimeUnit unit) throws InterruptedException, TimeoutException {
        validateUrl(originalUrl);
        String code = codePool.nextCode(timeout, unit);
        return domain + "/" + code;
    }

    /**
     * Fetches the next raw short code from the underlying pool.
     */
    public String nextCode() throws InterruptedException {
        return codePool.nextCode();
    }

    /**
     * Validates that the URL is a non-empty, valid HTTP or HTTPS URI.
     */
    public static void validateUrl(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("URL cannot be empty");
        }
        String trimmed = url.trim();
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            throw new IllegalArgumentException("Only http:// and https:// URLs are supported, got: " + url);
        }
        try {
            URI uri = URI.create(trimmed);
            if (uri.getHost() == null) {
                throw new IllegalArgumentException("Invalid URL format (missing host): " + url);
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Malformed URL: " + url, e);
        }
    }

    public String getDomain() {
        return domain;
    }

    public ShortCodePool getCodePool() {
        return codePool;
    }
}
