package com.preonsurl.apis.service;

import com.preonsurl.apis.cache.CachedShortUrl;
import com.preonsurl.apis.cache.ShortUrlLruCache;
import com.preonsurl.apis.entity.ShortUrl;
import com.preonsurl.apis.event.ShortUrlServedEvent;
import com.preonsurl.apis.exception.UrlExpiredException;
import com.preonsurl.apis.exception.UrlUsageLimitExceededException;
import com.preonsurl.apis.repository.ShortUrlRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;

@Service
public class ShortUrlServingCacheService {

    private static final Logger log = LoggerFactory.getLogger(ShortUrlServingCacheService.class);

    private final ShortUrlLruCache lruCache;
    private final ShortUrlRepository repository;
    private final ApplicationEventPublisher eventPublisher;

    public ShortUrlServingCacheService(ShortUrlLruCache lruCache,
                                       ShortUrlRepository repository,
                                       ApplicationEventPublisher eventPublisher) {
        this.lruCache = lruCache;
        this.repository = repository;
        this.eventPublisher = eventPublisher;
    }

    public Optional<String> resolveAndServe(String dirType,
                                            String shortCode,
                                            String ipAddress,
                                            String userAgent,
                                            String referer) {
        String normalizedDirType = normalizeDirType(dirType);

        // 1. Check LRU Cache
        CachedShortUrl cached = lruCache.get(normalizedDirType, shortCode);

        if (cached != null) {
            // Check if cached entry has expired or breached usage limit
            if (cached.isExpired()) {
                log.info("LRU cache entry expired for code='{}', removing from cache", shortCode);
                lruCache.remove(normalizedDirType, shortCode);
                verifyDbStateAndThrow(normalizedDirType, shortCode);
                throw new UrlExpiredException("Short URL has expired");
            }

            if (cached.isUsageLimitBreached()) {
                log.info("LRU cache entry usage limit reached for code='{}', removing from cache", shortCode);
                lruCache.remove(normalizedDirType, shortCode);
                verifyDbStateAndThrow(normalizedDirType, shortCode);
                throw new UrlUsageLimitExceededException("Short URL usage limit reached");
            }

            // Valid cache hit
            long currentUsage = cached.incrementClickCount();
            if (cached.getUsageLimit() != null && currentUsage >= cached.getUsageLimit()) {
                log.info("Short URL reached usage limit on this serve: code='{}', removing from LRU", shortCode);
                lruCache.remove(normalizedDirType, shortCode);
            }

            // Trigger background event to update DB click count, access log, and re-verify
            eventPublisher.publishEvent(new ShortUrlServedEvent(
                    cached.getId(),
                    cached.getShortCode(),
                    cached.getDirType(),
                    ipAddress,
                    userAgent,
                    referer
            ));

            return Optional.of(cached.getOriginalUrl());
        }

        // 2. Cache miss: Check in DB
        Optional<ShortUrl> dbOptional;
        if (normalizedDirType != null) {
            dbOptional = repository.findByDirTypeAndShortCode(normalizedDirType, shortCode);
        } else {
            dbOptional = repository.findByShortCode(shortCode);
        }

        if (dbOptional.isEmpty()) {
            return Optional.empty();
        }

        ShortUrl entity = dbOptional.get();

        // Check if expired in DB
        if (entity.getExpireAt() != null && Instant.now().isAfter(entity.getExpireAt())) {
            log.warn("Short URL expired in DB: code='{}', expireAt='{}'", shortCode, entity.getExpireAt());
            throw new UrlExpiredException("Short URL has expired");
        }

        // Check if usage limit breached in DB
        if (entity.getUsageLimit() != null && entity.getClickCount() >= entity.getUsageLimit()) {
            log.warn("Short URL usage limit breached in DB: code='{}', clickCount='{}', usageLimit='{}'",
                    shortCode, entity.getClickCount(), entity.getUsageLimit());
            throw new UrlUsageLimitExceededException("Short URL usage limit reached");
        }

        // Not expired and not breached: Put into LRU cache if usage remaining after this serve
        CachedShortUrl newCacheEntry = new CachedShortUrl(
                entity.getId(),
                entity.getShortCode(),
                entity.getDirType(),
                entity.getOriginalUrl(),
                entity.getExpireAt(),
                entity.getUsageLimit(),
                entity.getClickCount()
        );

        long newUsage = newCacheEntry.incrementClickCount();
        if (newCacheEntry.getUsageLimit() == null || newUsage < newCacheEntry.getUsageLimit()) {
            lruCache.put(newCacheEntry);
        }

        // Trigger background event
        eventPublisher.publishEvent(new ShortUrlServedEvent(
                entity.getId(),
                entity.getShortCode(),
                entity.getDirType(),
                ipAddress,
                userAgent,
                referer
        ));

        return Optional.of(entity.getOriginalUrl());
    }

    private void verifyDbStateAndThrow(String dirType, String shortCode) {
        Optional<ShortUrl> dbOptional = (dirType != null)
                ? repository.findByDirTypeAndShortCode(dirType, shortCode)
                : repository.findByShortCode(shortCode);

        if (dbOptional.isPresent()) {
            ShortUrl entity = dbOptional.get();
            if (entity.getExpireAt() != null && Instant.now().isAfter(entity.getExpireAt())) {
                throw new UrlExpiredException("Short URL has expired");
            }
            if (entity.getUsageLimit() != null && entity.getClickCount() >= entity.getUsageLimit()) {
                throw new UrlUsageLimitExceededException("Short URL usage limit reached");
            }
        }
    }

    public String normalizeDirType(String dirType) {
        if (dirType == null) {
            return null;
        }
        String trimmed = dirType.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        while (trimmed.startsWith("/")) {
            trimmed = trimmed.substring(1);
        }
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed.isEmpty() ? null : trimmed;
    }

    public ShortUrlLruCache getLruCache() {
        return lruCache;
    }
}
