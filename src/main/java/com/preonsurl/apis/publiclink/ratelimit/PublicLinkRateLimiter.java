package com.preonsurl.apis.publiclink.ratelimit;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class PublicLinkRateLimiter {

    public static final long RATE_LIMIT_WINDOW_MS = 5000L; // 5 seconds
    private final ConcurrentMap<String, Long> lastAccessTimePerIp = new ConcurrentHashMap<>();

    /**
     * Checks if the request from the client IP is allowed under the 1 request per 2 seconds rate limit.
     * Returns true if request is permitted, false if rate limited.
     */
    public boolean tryAcquire(String clientIp) {
        String key = (clientIp != null && !clientIp.isBlank()) ? clientIp.trim() : "UNKNOWN";
        long now = System.currentTimeMillis();

        // Evict stale entries if map gets large
        if (lastAccessTimePerIp.size() > 10000) {
            lastAccessTimePerIp.entrySet().removeIf(entry -> (now - entry.getValue()) > 60000);
        }

        Long previous = lastAccessTimePerIp.get(key);
        if (previous != null && (now - previous) < RATE_LIMIT_WINDOW_MS) {
            return false;
        }

        lastAccessTimePerIp.put(key, now);
        return true;
    }

    public long getRetryAfterSeconds() {
        return 2L;
    }

    public void clear() {
        lastAccessTimePerIp.clear();
    }
}
