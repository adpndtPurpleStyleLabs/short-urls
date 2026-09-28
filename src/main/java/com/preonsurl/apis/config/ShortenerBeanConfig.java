package com.preonsurl.apis.config;

import com.preonsurl.core.ShortCodePool;
import com.preonsurl.core.UrlShortenerCore;
import com.preonsurl.coreconfig.constants.CoreConfigKeys;
import com.preonsurl.coreconfig.service.CoreConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.util.Arrays;

@Configuration
public class ShortenerBeanConfig {
    private static final Logger log = LoggerFactory.getLogger(ShortenerBeanConfig.class);

    private final String domain;
    private final String secret;
    private final int workerCount;
    private final int bucketCapacity;

    private final Environment environment;

    public ShortenerBeanConfig(Environment environment, CoreConfigService coreConfigService) {
        this.environment = environment;
        this.domain = coreConfigService.get(CoreConfigKeys.App.SECURE_DOMAIN);
        this.secret = coreConfigService.get(CoreConfigKeys.Shortener.SECRET);
        this.workerCount = coreConfigService.getInt(CoreConfigKeys.Shortener.WORKER_COUNT);
        this.bucketCapacity = coreConfigService.getInt(CoreConfigKeys.Shortener.BUCKET_CAPACITY);
    }

    @Bean(destroyMethod = "close")
    public ShortCodePool shortCodePool() throws InterruptedException {
        if (!isAppProfileActive()) {
            log.info("ShortCodePool is disabled because active profiles {} do not include 'app'. Initializing empty pool.", Arrays.toString(environment.getActiveProfiles()));
            return ShortCodePool.empty();
        }

        log.info("Starting ShortCodePool for 'app' profile with {} workers, capacity {} per worker, domain={}", workerCount, bucketCapacity, domain);
        ShortCodePool pool = new ShortCodePool(workerCount, bucketCapacity, secret);
        pool.start();
        log.info("ShortCodePool initialized successfully. Available pre-buffered codes: {}", pool.totalAvailableCodes());
        return pool;
    }

    @Bean
    public UrlShortenerCore urlShortenerCore(ShortCodePool pool) {
        return new UrlShortenerCore(pool, domain);
    }

    private boolean isAppProfileActive() {
        if (environment == null) {
            return false;
        }
        for (String profile : environment.getActiveProfiles()) {
            if (profile != null && (profile.equalsIgnoreCase("app"))) {
                return true;
            }
        }
        return false;
    }
}
