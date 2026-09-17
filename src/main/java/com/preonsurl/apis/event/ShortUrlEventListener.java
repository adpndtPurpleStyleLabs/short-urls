package com.preonsurl.apis.event;

import com.preonsurl.apis.cache.ShortUrlLruCache;
import com.preonsurl.apis.entity.ShortUrl;
import com.preonsurl.apis.entity.ShortUrlAccessLog;
import com.preonsurl.apis.repository.ShortUrlAccessLogRepository;
import com.preonsurl.apis.repository.ShortUrlRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Component
public class ShortUrlEventListener {

    private static final Logger log = LoggerFactory.getLogger(ShortUrlEventListener.class);

    private final ShortUrlRepository repository;
    private final ShortUrlAccessLogRepository accessLogRepository;
    private final ShortUrlLruCache lruCache;

    public ShortUrlEventListener(ShortUrlRepository repository,
                                 ShortUrlAccessLogRepository accessLogRepository,
                                 ShortUrlLruCache lruCache) {
        this.repository = repository;
        this.accessLogRepository = accessLogRepository;
        this.lruCache = lruCache;
    }

    @Async
    @EventListener
    @Transactional
    public void onShortUrlServed(ShortUrlServedEvent event) {
        try {
            // 1. Increment click count in DB
            repository.incrementClickCount(event.shortUrlId());

            // 2. Save access log
            ShortUrlAccessLog accessLog = new ShortUrlAccessLog(
                    event.shortUrlId(),
                    event.shortCode(),
                    event.ipAddress(),
                    event.userAgent(),
                    event.referer()
            );
            accessLogRepository.save(accessLog);

            // 3. Check DB state to verify expiration and usage limit
            Optional<ShortUrl> updated = repository.findById(event.shortUrlId());
            if (updated.isPresent()) {
                ShortUrl entity = updated.get();
                boolean expired = entity.getExpireAt() != null && Instant.now().isAfter(entity.getExpireAt());
                boolean limitReached = entity.getUsageLimit() != null && entity.getClickCount() >= entity.getUsageLimit();

                if (expired || limitReached) {
                    log.info("Removing breached/expired short code '{}' (dirType='{}') from LRU cache [expired={}, limitReached={}]",
                            event.shortCode(), event.dirType(), expired, limitReached);
                    lruCache.remove(event.dirType(), event.shortCode());
                }
            }
        } catch (Exception e) {
            log.error("Failed to process ShortUrlServedEvent for short code: {}", event.shortCode(), e);
        }
    }
}
