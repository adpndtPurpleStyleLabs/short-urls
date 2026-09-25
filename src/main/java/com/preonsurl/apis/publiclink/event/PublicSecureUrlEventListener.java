package com.preonsurl.apis.publiclink.event;

import com.preonsurl.apis.publiclink.cache.PublicSecureUrlLruCache;
import com.preonsurl.apis.publiclink.dto.CachedPublicSecureUrlDto;
import com.preonsurl.apis.publiclink.entity.PublicSecureUrl;
import com.preonsurl.apis.publiclink.entity.PublicSecureUrlAccessLog;
import com.preonsurl.apis.publiclink.repository.PublicSecureUrlAccessLogRepository;
import com.preonsurl.apis.publiclink.repository.PublicSecureUrlRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class PublicSecureUrlEventListener {

    private static final Logger log = LoggerFactory.getLogger(PublicSecureUrlEventListener.class);

    private final PublicSecureUrlRepository repository;
    private final PublicSecureUrlAccessLogRepository accessLogRepository;
    private final PublicSecureUrlLruCache lruCache;

    public PublicSecureUrlEventListener(PublicSecureUrlRepository repository,
                                       PublicSecureUrlAccessLogRepository accessLogRepository,
                                       PublicSecureUrlLruCache lruCache) {
        this.repository = repository;
        this.accessLogRepository = accessLogRepository;
        this.lruCache = lruCache;
    }

    @Async
    @EventListener
    @Transactional
    public void onPublicSecureUrlCreated(PublicSecureUrlCreatedEvent event) {
        try {
            PublicSecureUrl entity = new PublicSecureUrl(
                    event.shortKey(),
                    event.originalUrl(),
                    event.psecureUrl(),
                    event.linkMode(),
                    event.pin(),
                    event.password(),
                    event.mode(),
                    event.accessPoliciesJson(),
                    event.ipAddress(),
                    event.userAgent(),
                    event.createdAt(),
                    event.userId()
            );

            PublicSecureUrl saved = repository.save(entity);
            log.info("Persisted PublicSecureUrl in background: id={}, shortKey='{}', psecureUrl='{}'",
                    saved.getId(), saved.getShortKey(), saved.getPsecureUrl());

            // Update cached entry with saved database ID
            CachedPublicSecureUrlDto cached = lruCache.get(event.shortKey());
            if (cached != null) {
                cached.setId(saved.getId());
            }
        } catch (Exception e) {
            log.error("Failed to asynchronously persist PublicSecureUrl for shortKey='{}'", event.shortKey(), e);
        }
    }

    @Async
    @EventListener
    @Transactional
    public void onPublicSecureUrlServed(PublicSecureUrlServedEvent event) {
        try {
            PublicSecureUrlAccessLog accessLog = new PublicSecureUrlAccessLog(
                    event.shortKey(),
                    event.publicSecureUrlId(),
                    event.ipAddress(),
                    event.userAgent(),
                    event.referer(),
                    event.accessedAt(),
                    event.status()
            );
            accessLogRepository.save(accessLog);

            if (event.publicSecureUrlId() != null) {
                repository.incrementClickCount(event.publicSecureUrlId());
            }

            log.debug("Recorded public secure access log for shortKey='{}' [IP={}]", event.shortKey(), event.ipAddress());
        } catch (Exception e) {
            log.error("Failed to record PublicSecureUrl access log for shortKey='{}'", event.shortKey(), e);
        }
    }
}
