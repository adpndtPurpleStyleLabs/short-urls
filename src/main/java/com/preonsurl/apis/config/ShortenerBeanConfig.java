package com.preonsurl.apis.config;

import com.preonsurl.core.ShortCodePool;
import com.preonsurl.core.UrlShortenerCore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.util.Arrays;

@Configuration
public class ShortenerBeanConfig {

    private static final Logger log = LoggerFactory.getLogger(ShortenerBeanConfig.class);

    @Value("${preonsurl.shortener.worker-count:4}")
    private int workerCount;

    @Value("${preonsurl.shortener.bucket-capacity:100}")
    private int bucketCapacity;

    @Value("${preonsurl.shortener.secret:PREONS-URL-SECRET-KEY-123456789}")
    private String secret;

    @Value("${preonsurl.shortener.domain:http://localhost:8081}")
    private String domain;

    private final Environment environment;

    public ShortenerBeanConfig(Environment environment) {
        this.environment = environment;
    }

    @Bean(destroyMethod = "close")
    public ShortCodePool shortCodePool() throws InterruptedException {
        if (!isAppProfileActive()) {
            log.info("ShortCodePool is disabled because active profiles {} do not include 'app'. Initializing empty pool.",
                    Arrays.toString(environment.getActiveProfiles()));
            return ShortCodePool.empty();
        }

        log.info("Starting ShortCodePool for 'app' profile with {} workers, capacity {} per worker, domain={}",
                workerCount, bucketCapacity, domain);
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
            if (profile != null && (profile.equalsIgnoreCase("app")
                    || profile.toLowerCase().startsWith("app-")
                    || profile.toLowerCase().startsWith("app_"))) {
                return true;
            }
        }
        return false;
    }
}
