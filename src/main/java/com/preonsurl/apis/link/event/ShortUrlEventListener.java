package com.preonsurl.apis.link.event;

import com.preonsurl.apis.link.cache.NewUrlLruCache;
import com.preonsurl.apis.link.entity.NewUrl;
import com.preonsurl.apis.link.entity.NewUrlAccessLog;
import com.preonsurl.apis.link.repository.NewUrlAccessLogRepository;
import com.preonsurl.apis.link.repository.NewUrlRepository;
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

    private final NewUrlRepository repository;
    private final NewUrlAccessLogRepository accessLogRepository;
    private final NewUrlLruCache lruCache;

    public ShortUrlEventListener(NewUrlRepository repository,
                                 NewUrlAccessLogRepository accessLogRepository,
                                 NewUrlLruCache lruCache) {
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
            NewUrlAccessLog accessLog = new NewUrlAccessLog(
                    event.shortUrlId(),
                    event.fullShortUrl(),
                    event.ipAddress(),
                    event.userAgent(),
                    event.referer()
            );
            accessLogRepository.save(accessLog);

            // 3. Check DB state to verify expiration and usage limit
            Optional<NewUrl> updated = repository.findById(event.shortUrlId());
            if (updated.isPresent()) {
                NewUrl entity = updated.get();
                boolean expired = entity.getExpireAt() != null && Instant.now().isAfter(entity.getExpireAt());
                boolean limitReached = entity.getUsageLimit() != null && entity.getClickCount() >= entity.getUsageLimit();

                if (expired || limitReached) {
                    log.info("Removing breached/expired short code '{}' from LRU cache [expired={}, limitReached={}]",
                            event.fullShortUrl(),  expired, limitReached);
                    lruCache.remove(event.fullShortUrl());
                }
            }
        } catch (Exception e) {
            log.error("Failed to process ShortUrlServedEvent for full url: {}", event.fullShortUrl(), e);
        }
    }
}
