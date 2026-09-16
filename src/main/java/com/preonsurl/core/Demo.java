package com.preonsurl.core;

public final class Demo {
    public static void main(String[] args) throws Exception {
        int workerCount = args.length > 0 ? Integer.parseInt(args[0]) : 4;
        int bucketCapacity = args.length > 1 ? Integer.parseInt(args[1]) : 1000;
        String secret = args.length > 2 ? args[2] : "THIS-IS-A-DEMO-SECRET-123456789";

        try (ShortCodePool pool = new ShortCodePool(workerCount, bucketCapacity, secret)) {
            pool.start();

            System.out.println("Workers started: " + pool.workerCount());
            System.out.println("Codes available after startup: " + pool.totalAvailableCodes());

            UrlShortenerCore shortener = new UrlShortenerCore(pool, "https://short.my");

            String[] urls = {
                    "https://example.com/products/iphone",
                    "https://example.com/blog/core-java-url-shortener",
                    "https://www.google.com/search?q=java"
            };

            for (String url : urls) {
                String shortUrl = shortener.shorten(url);
                String code = shortUrl.substring(shortUrl.lastIndexOf('/') + 1);
                System.out.println(url);
                System.out.println("  -> " + shortUrl);
            }

            Thread.sleep(200);
            System.out.println("Codes available after requests/refill: " + pool.totalAvailableCodes());
        }
    }
}
