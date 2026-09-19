package com.preonsurl.apis.link.service;

import com.preonsurl.apis.link.cache.CachedNewUrlDto;
import com.preonsurl.apis.link.cache.NewUrlLruCache;
import com.preonsurl.apis.link.entity.NewUrl;
import com.preonsurl.apis.link.event.ShortUrlServedEvent;
import com.preonsurl.apis.link.exception.UrlExpiredException;
import com.preonsurl.apis.link.exception.UrlUsageLimitExceededException;
import com.preonsurl.apis.link.repository.NewUrlRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;

@Service
public class NewUrlServingCacheService {

    private static final Logger log = LoggerFactory.getLogger(NewUrlServingCacheService.class);

    private final NewUrlLruCache lruCache;
    private final NewUrlRepository repository;
    private final ApplicationEventPublisher eventPublisher;

    public NewUrlServingCacheService(NewUrlLruCache lruCache,
                                     NewUrlRepository repository,
                                     ApplicationEventPublisher eventPublisher) {
        this.lruCache = lruCache;
        this.repository = repository;
        this.eventPublisher = eventPublisher;
    }

    public Optional<String> resolveAndServe(String fullUrl, String ipAddress, String userAgent, String referer) {
        // 1. Check LRU Cache
        CachedNewUrlDto cached = lruCache.get(fullUrl);

        if (cached != null) {
            // Check if cached entry has expired or breached usage limit
            if (cached.isExpired()) {
                log.info("LRU cache entry expired for code='{}', removing from cache", fullUrl);
                lruCache.remove(cached.getFullShortUrl());
                verifyDbStateAndThrow(fullUrl);
                throw new UrlExpiredException("Short URL has expired");
            }

            if (cached.isUsageLimitBreached()) {
                log.info("LRU cache entry usage limit reached for code='{}', removing from fullUrl", fullUrl);
                lruCache.remove(fullUrl);
                verifyDbStateAndThrow(fullUrl);
                throw new UrlUsageLimitExceededException("Short URL usage limit reached");
            }

            // Valid cache hit
            long currentUsage = cached.incrementClickCount();
            if (cached.getUsageLimit() != null && currentUsage >= cached.getUsageLimit()) {
                log.info("Short URL reached usage limit on this serve: code='{}', removing from LRU", fullUrl);
                lruCache.remove(fullUrl);
            }

            // Trigger background event to update DB click count, access log, and re-verify
            eventPublisher.publishEvent(new ShortUrlServedEvent(cached.getId(), cached.getFullShortUrl(), ipAddress, userAgent, referer));
            return Optional.of(cached.getOriginalUrl());
        }

        // 2. Cache miss: Check in DB
        Optional<NewUrl> dbOptional = repository.findByFullShortUrl(fullUrl);

        if (dbOptional.isEmpty()) {
            return Optional.empty();
        }

        NewUrl entity = dbOptional.get();

        // Check if expired in DB
        if (entity.getExpireAt() != null && Instant.now().isAfter(entity.getExpireAt())) {
            log.warn("Short URL expired in DB: url='{}', expireAt='{}'", entity.getOriginalUrl(), entity.getExpireAt());
            throw new UrlExpiredException("Short URL has expired");
        }

        // Check if usage limit breached in DB
        if (entity.getUsageLimit() != null && entity.getClickCount() >= entity.getUsageLimit()) {
            log.warn("Short URL usage limit breached in DB: url='{}', clickCount='{}', usageLimit='{}'", entity.getOriginalUrl(), entity.getClickCount(), entity.getUsageLimit());
            throw new UrlUsageLimitExceededException("Short URL usage limit reached");
        }

        // Not expired and not breached: Put into LRU cache if usage remaining after this serve
        CachedNewUrlDto newCacheEntry = new CachedNewUrlDto(entity.getId(), entity.getFullShortUrl(), entity.getOriginalUrl(), entity.getExpireAt(), entity.getUsageLimit(), entity.getClickCount());

        long newUsage = newCacheEntry.incrementClickCount();
        if (newCacheEntry.getUsageLimit() == null || newUsage < newCacheEntry.getUsageLimit()) {
            lruCache.put(newCacheEntry);
        }

        // Trigger background event
        eventPublisher.publishEvent(new ShortUrlServedEvent(entity.getId(), entity.getFullShortUrl(), ipAddress, userAgent, referer));
        return Optional.of(entity.getOriginalUrl());
    }

    private void verifyDbStateAndThrow(String fullShortUrl) {
        Optional<NewUrl> dbOptional = repository.findByFullShortUrl(fullShortUrl);

        if (dbOptional.isPresent()) {
            NewUrl entity = dbOptional.get();
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

    public NewUrlLruCache getLruCache() {
        return lruCache;
    }
}
