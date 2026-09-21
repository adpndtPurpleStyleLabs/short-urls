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
    private final com.preonsurl.apis.link.repository.UsagePolicyRepository usagePolicyRepository;

    public ShortUrlEventListener(NewUrlRepository repository,
                                 NewUrlAccessLogRepository accessLogRepository,
                                 NewUrlLruCache lruCache,
                                 com.preonsurl.apis.link.repository.UsagePolicyRepository usagePolicyRepository) {
        this.repository = repository;
        this.accessLogRepository = accessLogRepository;
        this.lruCache = lruCache;
        this.usagePolicyRepository = usagePolicyRepository;
    }

    @Async
    @EventListener
    @Transactional
    public void onShortUrlServed(ShortUrlServedEvent event) {
        try {
            // 1. Increment click count in DB
            repository.incrementClickCount(event.shortUrlId());

            // 2. Increment usage in usage_policies table
            usagePolicyRepository.incrementUsage(event.shortUrlId());

            // 3. Save access log
            NewUrlAccessLog accessLog = new NewUrlAccessLog(
                    event.shortUrlId(),
                    event.newUrl(),
                    event.ipAddress(),
                    event.userAgent(),
                    event.referer()
            );
            accessLogRepository.save(accessLog);

            // 4. Check DB state to verify expiration, usage limit, and active status
            Optional<NewUrl> updated = repository.findById(event.shortUrlId());
            if (updated.isPresent()) {
                NewUrl entity = updated.get();
                boolean expired = entity.getExpireAt() != null && Instant.now().isAfter(entity.getExpireAt());
                boolean limitReached = entity.getUsageLimit() != null && entity.getClickCount() >= entity.getUsageLimit();
                boolean inactive = !entity.isActive();

                // Also check UsagePolicy if limit reached or expired
                Optional<com.preonsurl.apis.link.entity.UsagePolicy> upOpt = usagePolicyRepository.findByShortUrlId(event.shortUrlId());
                boolean policyExhausted = upOpt.map(com.preonsurl.apis.link.entity.UsagePolicy::isExhausted).orElse(false);

                if (expired || limitReached || inactive || policyExhausted) {
                    log.info("Removing breached/expired/inactive short code '{}' from LRU cache [expired={}, limitReached={}, inactive={}, policyExhausted={}]",
                            event.newUrl(), expired, limitReached, inactive, policyExhausted);
                    lruCache.remove(event.newUrl());
                }
            }

        } catch (Exception e) {
            log.error("Failed to process ShortUrlServedEvent for full url: {}", event.newUrl(), e);
        }
    }
}
