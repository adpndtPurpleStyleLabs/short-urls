package com.preonsurl.core;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class UrlShortenerCore {
    private final ShortCodePool codePool;
    private final String domain;

    public UrlShortenerCore(ShortCodePool codePool , String domain) {
        if (codePool == null ) throw new IllegalArgumentException("pool is required");
        if (domain == null || domain.isBlank()) throw new IllegalArgumentException("domain is required");
        this.codePool = codePool;
        this.domain = domain.endsWith("/") ? domain.substring(0, domain.length() - 1) : domain;
    }

    public String shorten(String originalUrl) throws InterruptedException, TimeoutException {
        return shorten(originalUrl, 5, TimeUnit.SECONDS);
    }

    public String shorten(String originalUrl, long timeout, TimeUnit unit) throws InterruptedException, TimeoutException {
        validateUrl(originalUrl);
        String code = codePool.nextCode(timeout, unit);
        return domain + "/" + code;
    }

    private void validateUrl(String url) {
        if (url == null || url.isBlank()) throw new IllegalArgumentException("URL cannot be empty");
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            throw new IllegalArgumentException("Only http/https URLs are supported");
        }
    }
}
