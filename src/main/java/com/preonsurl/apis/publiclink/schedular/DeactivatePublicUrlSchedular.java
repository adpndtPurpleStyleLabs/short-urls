package com.preonsurl.apis.publiclink.schedular;

import com.preonsurl.apis.publiclink.cache.PublicSecureUrlLruCache;
import com.preonsurl.apis.publiclink.entity.PublicSecureUrl;
import com.preonsurl.apis.publiclink.repository.PublicSecureUrlRepository;
import com.preonsurl.coreconfig.constants.CoreConfigKeys;
import com.preonsurl.coreconfig.service.CoreConfigService;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Component
public class DeactivatePublicUrlSchedular {
    private static final Logger log = LoggerFactory.getLogger(DeactivatePublicUrlSchedular.class);
    private final int EXPIRY_DAYS;
    private final PublicSecureUrlRepository publicSecureUrlRepository;
    private final PublicSecureUrlLruCache publicSecureUrlLruCache;

    public DeactivatePublicUrlSchedular(
            PublicSecureUrlRepository publicSecureUrlRepository,
            PublicSecureUrlLruCache publicSecureUrlLruCache,
            CoreConfigService coreConfigService
    ) {
        this.publicSecureUrlRepository = publicSecureUrlRepository;
        this.publicSecureUrlLruCache = publicSecureUrlLruCache;
        this.EXPIRY_DAYS = coreConfigService.getInt(CoreConfigKeys.App.PSECURE_URL_EXPIRY_DAYS);
    }

    /**
     * Deactivates public secure URLs older than 15 days
     * and removes them from the LRU cache.
     *
     * Runs every day at 02:00 server time.
     */
    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void deactivateExpiredPublicUrls() {
        Instant cutoff = Instant.now().minus(EXPIRY_DAYS, ChronoUnit.DAYS);
        List<PublicSecureUrl> expiredUrls = publicSecureUrlRepository.findActiveUrlsOlderThan(cutoff);

        if (expiredUrls.isEmpty()) {
            log.debug("No public secure URLs found older than {} days", EXPIRY_DAYS);
            return;
        }

        int deactivatedCount = 0;
        int cacheRemovedCount = 0;

        for (PublicSecureUrl url : expiredUrls) {
            url.setActive(false);
            if (url.getShortKey() != null) {
                if (publicSecureUrlLruCache.remove(url.getShortKey()) != null) {
                    cacheRemovedCount++;
                }
            }

            if (url.getPsecureUrl() != null) {
                publicSecureUrlLruCache.remove(url.getPsecureUrl());
            }
            deactivatedCount++;
        }
        publicSecureUrlRepository.saveAll(expiredUrls);
        log.info("Deactivated {} public secure URLs older than {} days. " + "Removed {} entries from LRU cache. Cutoff={}", deactivatedCount, EXPIRY_DAYS, cacheRemovedCount, cutoff);
    }
}
