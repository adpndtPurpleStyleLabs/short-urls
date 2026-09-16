package com.preonsurl.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Standalone demo demonstrating the core short code engine in action.
 */
public final class Demo {

    private static final Logger log = LoggerFactory.getLogger(Demo.class);

    public static void main(String[] args) throws Exception {
        int workerCount = args.length > 0 ? Integer.parseInt(args[0]) : 4;
        int bucketCapacity = args.length > 1 ? Integer.parseInt(args[1]) : 100;
        String secret = args.length > 2 ? args[2] : "PREONS-DEMO-SECRET-KEY-123456789";

        log.info("Starting PreonsURL Core Engine Demo...");
        try (ShortCodePool pool = new ShortCodePool(workerCount, bucketCapacity, secret)) {
            pool.start();

            log.info("Workers started: {}", pool.workerCount());
            log.info("Codes available after startup: {}", pool.totalAvailableCodes());

            UrlShortenerCore shortener = new UrlShortenerCore(pool, "https://short.preons.dev");

            String[] urls = {
                    "https://example.com/products/iphone",
                    "https://example.com/blog/core-java-url-shortener",
                    "https://www.google.com/search?q=java"
            };

            for (String url : urls) {
                String shortUrl = shortener.shorten(url);
                String code = shortUrl.substring(shortUrl.lastIndexOf('/') + 1);
                log.info("Original: {} -> Short: {} (Code: {})", url, shortUrl, code);
            }

            Thread.sleep(100);
            log.info("Codes available after refill: {}", pool.totalAvailableCodes());
        }
    }
}
