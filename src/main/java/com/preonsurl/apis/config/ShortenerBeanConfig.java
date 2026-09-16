package com.preonsurl.apis.config;

import com.preonsurl.core.ShortCodePool;
import com.preonsurl.core.UrlShortenerCore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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

    @Bean(destroyMethod = "close")
    public ShortCodePool shortCodePool() throws InterruptedException {
        log.info("Starting ShortCodePool with {} workers, capacity {} per worker, domain={}",
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
}
