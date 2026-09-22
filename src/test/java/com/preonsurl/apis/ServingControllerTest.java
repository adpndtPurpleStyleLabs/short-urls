package com.preonsurl.apis;

import com.preonsurl.apis.link.entity.NewUrl;
import com.preonsurl.apis.link.entity.NewUrlAccessLog;
import com.preonsurl.apis.link.repository.NewUrlAccessLogRepository;
import com.preonsurl.apis.link.repository.NewUrlRepository;
import com.preonsurl.apis.link.service.NewUrlServingCacheService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import com.preonsurl.apis.link.enums.LinkMode;
import com.preonsurl.apis.link.service.ProxyResourceValidator;
import com.sun.net.httpserver.HttpServer;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ServingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private NewUrlRepository shortUrlRepository;

    @Autowired
    private NewUrlAccessLogRepository accessLogRepository;

    @Autowired
    private NewUrlServingCacheService servingCacheService;

    @BeforeEach
    void setUp() {
        servingCacheService.getLruCache().clear();
        accessLogRepository.deleteAll();
        shortUrlRepository.deleteAll();
    }

    @Test
    void rootEndpointReturnsApiStatus() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.service").value("007"))
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void servingRootShortCodeRedirectsToOriginalUrlAndLogsAccess() throws Exception {
        NewUrl newUrl = new NewUrl(
                "abcXYZ1",
                "https://example.com/target-page",
                "http://localhost/abcXYZ1"
        );
        NewUrl saved = shortUrlRepository.save(newUrl);

        mockMvc.perform(get("/abcXYZ1")
                        .header("X-Forwarded-For", "203.0.113.195")
                        .header("User-Agent", "PreonsBrowser/1.0")
                        .header("Referer", "https://google.com"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/target-page"));

        // Verify placed in LRU cache
        assertNotNull(servingCacheService.getLruCache().get("http://localhost/abcXYZ1"), "Should be placed in LRU cache");

        // Verify background event increments click count and logs access
        await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> {
            NewUrl updated = shortUrlRepository.findByShortCode("abcXYZ1").orElseThrow();
            assertEquals(1, updated.getClickCount(), "Click count should be incremented to 1");

            List<NewUrlAccessLog> logs = accessLogRepository.findByShortUrlIdOrderByAccessedAtDesc(saved.getId());
            assertEquals(1, logs.size(), "One access log record should be created");
            NewUrlAccessLog log = logs.get(0);
            assertEquals(saved.getId(), log.getShortUrlId());
            assertEquals("http://localhost/abcXYZ1", log.getShortCode());
            assertEquals("203.0.113.195", log.getIpAddress());
            assertEquals("PreonsBrowser/1.0", log.getUserAgent());
            assertEquals("https://google.com", log.getReferer());
        });
    }

    @Test
    void servingDirectoryShortCodeRedirectsToOriginalUrlAndLogsAccess() throws Exception {
        NewUrl newUrl = new NewUrl(
                "inv1234",
                "https://example.com/billing/invoice/456",
                "http://localhost/invoice/inv1234"
        );
        NewUrl saved = shortUrlRepository.save(newUrl);

        mockMvc.perform(get("/invoice/inv1234")
                        .header("User-Agent", "TestAgent"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/billing/invoice/456"));

        assertNotNull(servingCacheService.getLruCache().get("http://localhost/invoice/inv1234"), "Should be placed in LRU cache");

        await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> {
            NewUrl updated = shortUrlRepository.findByShortCode("inv1234").orElseThrow();
            assertEquals(1, updated.getClickCount(), "Click count should be incremented to 1");

            List<NewUrlAccessLog> logs = accessLogRepository.findByShortUrlIdOrderByAccessedAtDesc(saved.getId());
            assertEquals(1, logs.size(), "Access log should be saved for directory short code");
            assertEquals("http://localhost/invoice/inv1234", logs.get(0).getShortCode());
            assertEquals("TestAgent", logs.get(0).getUserAgent());
        });
    }

    @Test
    void servingNonExistentShortCodeReturns404NotFoundAndDoesNotLog() throws Exception {
        mockMvc.perform(get("/nonexistent123"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("New URL not found"));

        assertEquals(0, accessLogRepository.count(), "No access log should be recorded for 404");
        assertNull(servingCacheService.getLruCache().get("http://localhost/nonexistent123"));
    }

    @Test
    void servingNonExistentDirectoryShortCodeReturns404NotFoundAndDoesNotLog() throws Exception {
        mockMvc.perform(get("/invoice/unknown999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("New URL not found"));

        assertEquals(0, accessLogRepository.count(), "No access log should be recorded for 404");
        assertNull(servingCacheService.getLruCache().get("http://localhost/invoice/unknown999"));
    }

    @Test
    void servingExpiredRootShortCodeReturns410GoneAndDoesNotLog() throws Exception {
        NewUrl newUrl = new NewUrl(
                "expRoot",
                "https://example.com/expired-root",
                "http://localhost/expRoot",
                Instant.now().minus(1, ChronoUnit.HOURS)
        );
        shortUrlRepository.save(newUrl);

        mockMvc.perform(get("/expRoot"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("New URL has expired"));

        assertEquals(0, accessLogRepository.count(), "No access log should be recorded for expired URL");
        assertNull(servingCacheService.getLruCache().get("http://localhost/expRoot"), "Expired URL must not be in LRU");
    }

    @Test
    void servingExpiredDirectoryShortCodeReturns410GoneAndDoesNotLog() throws Exception {
        NewUrl newUrl = new NewUrl(
                "expDir",
                "https://example.com/expired-dir",
                "http://localhost/promo/expDir",
                Instant.now().minus(1, ChronoUnit.HOURS)
        );
        shortUrlRepository.save(newUrl);

        mockMvc.perform(get("/promo/expDir"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("New URL has expired"));

        assertEquals(0, accessLogRepository.count(), "No access log should be recorded for expired URL");
        assertNull(servingCacheService.getLruCache().get("http://localhost/promo/expDir"), "Expired URL must not be in LRU");
    }

    @Test
    void servingShortCodeWithUsageLimitOnceSucceedsFirstTimeAndFailsSecondTime() throws Exception {
        NewUrl newUrl = new NewUrl(
                "onceCode",
                "https://example.com/one-time",
                "http://localhost/onceCode",
                Instant.now().plus(1, ChronoUnit.DAYS),
                1L
        );
        shortUrlRepository.save(newUrl);

        // 1st request -> Succeeds and redirects
        mockMvc.perform(get("/onceCode"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/one-time"));

        // Usage limit reached on 1st serve -> must be evicted from LRU cache
        assertNull(servingCacheService.getLruCache().get("http://localhost/onceCode"), "Breached limit must be evicted from LRU");

        await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> {
            NewUrl afterFirst = shortUrlRepository.findByShortCode("onceCode").orElseThrow();
            assertEquals(1, afterFirst.getClickCount(), "Usage should be incremented to 1");
            assertEquals(1, accessLogRepository.count(), "One access log should be recorded");
        });

        // 2nd request -> Exceeded limit -> checks in DB and responds as expired / limit reached
        mockMvc.perform(get("/onceCode"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("New URL usage limit reached"));

        NewUrl afterSecond = shortUrlRepository.findByShortCode("onceCode").orElseThrow();
        assertEquals(1, afterSecond.getClickCount(), "Usage should still be 1 (not incremented on exceeded)");
        assertEquals(1, accessLogRepository.count(), "Access log should not be recorded on exceeded");
    }

    @Test
    void servingDirectoryShortCodeWithUsageLimitEnforced() throws Exception {
        NewUrl newUrl = new NewUrl(
                "twoCode",
                "https://example.com/twice",
                "http://localhost/deals/twoCode",
                Instant.now().plus(1, ChronoUnit.DAYS),
                2L
        );
        shortUrlRepository.save(newUrl);

        // 1st request -> OK, in cache
        mockMvc.perform(get("/deals/twoCode"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/twice"));

        // 2nd request -> OK, limit reached, evicted from cache
        mockMvc.perform(get("/deals/twoCode"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/twice"));

        assertNull(servingCacheService.getLruCache().get("http://localhost/deals/twoCode"), "Should be evicted after 2nd serve");

        await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> {
            NewUrl afterSecond = shortUrlRepository.findByShortCode("twoCode").orElseThrow();
            assertEquals(2, afterSecond.getClickCount(), "Usage should be incremented to 2");
            assertEquals(2, accessLogRepository.count());
        });

        // 3rd request -> checks in DB, responds as limit reached
        mockMvc.perform(get("/deals/twoCode"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("New URL usage limit reached"));

        NewUrl afterThird = shortUrlRepository.findByShortCode("twoCode").orElseThrow();
        assertEquals(2, afterThird.getClickCount(), "Usage should not be incremented after reaching limit");
        assertEquals(2, accessLogRepository.count());
    }

    @Test
    void servingShortCodeWithUnlimitedUsageCanBeAccessedMultipleTimes() throws Exception {
        NewUrl newUrl = new NewUrl(
                "unlimitedCode",
                "https://example.com/unlimited",
                "http://localhost/unlimitedCode",
                Instant.now().plus(1, ChronoUnit.DAYS),
                null
        );
        shortUrlRepository.save(newUrl);

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(get("/unlimitedCode"))
                    .andExpect(status().isFound())
                    .andExpect(header().string("Location", "https://example.com/unlimited"));
        }

        assertNotNull(servingCacheService.getLruCache().get("http://localhost/unlimitedCode"), "Unlimited entry remains in LRU");

        await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> {
            NewUrl afterFive = shortUrlRepository.findByShortCode("unlimitedCode").orElseThrow();
            assertEquals(5, afterFive.getClickCount(), "Usage should be incremented to 5");
            assertEquals(5, accessLogRepository.count());
        });
    }

    @Test
    void servingCustomSlugRedirectsSuccessfully() throws Exception {
        NewUrl slugUrl = new NewUrl(
                "diwali-sale",
                "https://example.com/diwali-destination",
                "http://localhost/diwali-sale",
                Instant.now().plus(1, ChronoUnit.DAYS),
                null
        );
        shortUrlRepository.save(slugUrl);

        mockMvc.perform(get("/diwali-sale"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/diwali-destination"));

        NewUrl dirSlugUrl = new NewUrl(
                "spring-sale",
                "https://example.com/spring-destination",
                "http://localhost/deals/spring-sale",
                Instant.now().plus(1, ChronoUnit.DAYS),
                null
        );
        shortUrlRepository.save(dirSlugUrl);

        mockMvc.perform(get("/deals/spring-sale"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/spring-destination"));

        await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> {
            assertEquals(2, accessLogRepository.count(), "Both slug accesses should be logged");
        });
    }

    @Test
    void servingInactiveShortCodeReturns404NotFound() throws Exception {
        NewUrl inactiveUrl = new NewUrl(
                "inactive-code",
                "https://example.com/inactive-target",
                "http://localhost/inactive-code"
        );
        inactiveUrl.setActive(false);
        shortUrlRepository.save(inactiveUrl);

        mockMvc.perform(get("/inactive-code"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message", org.hamcrest.Matchers.containsString("not found")));
    }

    @Test
    void servingActiveUrlSavesActiveInLruAndCachedInactiveReturns404() throws Exception {
        NewUrl activeUrl = new NewUrl(
                "active-lru-code",
                "https://example.com/active-destination",
                "http://localhost/active-lru-code"
        );
        activeUrl.setActive(true);
        shortUrlRepository.save(activeUrl);

        // 1. Initial serve -> populates LRU cache
        mockMvc.perform(get("/active-lru-code"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/active-destination"));

        com.preonsurl.apis.link.cache.CachedNewUrlDto cached = servingCacheService.getLruCache().get("http://localhost/active-lru-code");
        assertNotNull(cached, "Should be cached in LRU");
        assertTrue(cached.isActive(), "isActive should be true in LRU cache");

        // 2. Put an inactive entry directly in LRU cache -> serving should reject with 404 and remove from LRU
        com.preonsurl.apis.link.cache.CachedNewUrlDto inactiveCached = new com.preonsurl.apis.link.cache.CachedNewUrlDto(
                9999L,
                "http://localhost/fake-inactive",
                "https://example.com/fake",
                null,
                null,
                0,
                false
        );
        servingCacheService.getLruCache().put(inactiveCached);

        mockMvc.perform(get("/fake-inactive"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));

        assertNull(servingCacheService.getLruCache().get("http://localhost/fake-inactive"), "Inactive entry should be removed from LRU");
    }

    @Test
    void servingProxyMode_servesUpstreamResourceWithoutChangingBrowserUrl() throws Exception {
        ProxyResourceValidator.allowLoopbackForTesting = true;
        // Start embedded HTTP server to simulate upstream resource server
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        byte[] pdfBytes = "%PDF-1.4 Mock Binary Content for PreonsURL".getBytes(StandardCharsets.UTF_8);

        server.createContext("/assets/document.pdf", exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "application/pdf");
            exchange.getResponseHeaders().set("Content-Disposition", "inline; filename=\"document.pdf\"");
            exchange.sendResponseHeaders(200, pdfBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(pdfBytes);
            }
        });
        server.start();
        int port = server.getAddress().getPort();

        try {
            String upstreamUrl = "http://127.0.0.1:" + port + "/assets/document.pdf";
            NewUrl proxyUrl = new NewUrl(
                    "proxy-pdf-code",
                    upstreamUrl,
                    null,
                    "http://localhost/proxy-pdf-code",
                    Instant.now().plus(30, ChronoUnit.DAYS),
                    null,
                    LinkMode.PROXY
            );
            NewUrl saved = shortUrlRepository.save(proxyUrl);

            // 1. Initial request (Cache Miss)
            mockMvc.perform(get("/proxy-pdf-code")
                            .header("User-Agent", "Mozilla/5.0")
                            .header("Accept", "application/pdf"))
                    .andExpect(status().isOk())
                    .andExpect(header().doesNotExist("Location"))
                    .andExpect(header().string("Content-Type", "application/pdf"))
                    .andExpect(header().string("Content-Disposition", "inline; filename=\"document.pdf\""))
                    .andExpect(content().bytes(pdfBytes));

            // Verify cached with PROXY mode
            com.preonsurl.apis.link.cache.CachedNewUrlDto cached =
                    servingCacheService.getLruCache().get("http://localhost/proxy-pdf-code");
            assertNotNull(cached, "Should be cached in LRU");
            assertEquals(LinkMode.PROXY, cached.getLinkMode());

            // 2. Second request (Cache Hit)
            mockMvc.perform(get("/proxy-pdf-code")
                            .header("User-Agent", "Mozilla/5.0")
                            .header("Accept", "application/pdf"))
                    .andExpect(status().isOk())
                    .andExpect(header().doesNotExist("Location"))
                    .andExpect(header().string("Content-Type", "application/pdf"))
                    .andExpect(content().bytes(pdfBytes));

            // Verify access log and click count
            await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> {
                NewUrl updated = shortUrlRepository.findByShortCode("proxy-pdf-code").orElseThrow();
                assertEquals(2, updated.getClickCount(), "Click count should be 2 for 2 proxied requests");
            });

        } finally {
            ProxyResourceValidator.allowLoopbackForTesting = false;
            server.stop(0);
        }
    }

    @Test
    void servingProxyMode_forwardsClientHeadersAndStreamsResponse() throws Exception {
        ProxyResourceValidator.allowLoopbackForTesting = true;
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        byte[] data = "zip-binary-data".getBytes(StandardCharsets.UTF_8);

        java.util.concurrent.atomic.AtomicReference<String> capturedUa = new java.util.concurrent.atomic.AtomicReference<>();
        server.createContext("/files/package.zip", exchange -> {
            capturedUa.set(exchange.getRequestHeaders().getFirst("User-Agent"));
            exchange.getResponseHeaders().set("Content-Type", "application/zip");
            exchange.sendResponseHeaders(200, data.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(data);
            }
        });
        server.start();
        int port = server.getAddress().getPort();

        try {
            String upstreamUrl = "http://127.0.0.1:" + port + "/files/package.zip";
            NewUrl proxyUrl = new NewUrl(
                    "proxy-zip-code",
                    upstreamUrl,
                    null,
                    "http://localhost/proxy-zip-code",
                    Instant.now().plus(30, ChronoUnit.DAYS),
                    null,
                    LinkMode.PROXY
            );
            shortUrlRepository.save(proxyUrl);

            // Client sends User-Agent
            mockMvc.perform(get("/proxy-zip-code")
                            .header("User-Agent", "CustomClient/2.0"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Content-Type", "application/zip"))
                    .andExpect(content().bytes(data));

            assertEquals("CustomClient/2.0", capturedUa.get());

        } finally {
            ProxyResourceValidator.allowLoopbackForTesting = false;
            server.stop(0);
        }
    }

    @Test
    void servingProxyMode_subResourcesWithReferer_proxiedSuccessfully() throws Exception {
        ProxyResourceValidator.allowLoopbackForTesting = true;
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);

        byte[] htmlBytes = "<html><body><img src=\"/pub/media/logo.svg\"></body></html>".getBytes(StandardCharsets.UTF_8);
        byte[] svgBytes = "<svg>mock svg</svg>".getBytes(StandardCharsets.UTF_8);

        java.util.concurrent.atomic.AtomicReference<String> capturedReferer = new java.util.concurrent.atomic.AtomicReference<>();

        server.createContext("/sale", exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(200, htmlBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(htmlBytes);
            }
        });

        server.createContext("/pub/media/logo.svg", exchange -> {
            capturedReferer.set(exchange.getRequestHeaders().getFirst("Referer"));
            exchange.getResponseHeaders().set("Content-Type", "image/svg+xml");
            exchange.sendResponseHeaders(200, svgBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(svgBytes);
            }
        });

        server.start();
        int port = server.getAddress().getPort();

        try {
            String upstreamBase = "http://127.0.0.1:" + port + "/sale";
            NewUrl proxyUrl = new NewUrl(
                    "ogaan-sale",
                    upstreamBase,
                    null,
                    "http://localhost/ogaan-sale",
                    Instant.now().plus(30, ChronoUnit.DAYS),
                    null,
                    LinkMode.PROXY
            );
            shortUrlRepository.save(proxyUrl);

            // 1. Initial page load sets PREONS_PROXY_CTX cookie
            mockMvc.perform(get("/ogaan-sale")
                            .header("User-Agent", "Mozilla/5.0"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("PREONS_PROXY_CTX=ogaan-sale")))
                    .andExpect(content().bytes(htmlBytes));

            // 2. Browser requests sub-resource with Referer: http://localhost/ogaan-sale
            mockMvc.perform(get("/pub/media/logo.svg")
                            .header("User-Agent", "Mozilla/5.0")
                            .header("Referer", "http://localhost/ogaan-sale"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Content-Type", "image/svg+xml"))
                    .andExpect(content().bytes(svgBytes));

            // Verify upstream received the upstream page as Referer (protecting against hotlink blocks)
            assertEquals(upstreamBase, capturedReferer.get());

        } finally {
            ProxyResourceValidator.allowLoopbackForTesting = false;
            server.stop(0);
        }
    }

    @Test
    void servingProxyMode_subResourcesWithCookieFallback_proxiedSuccessfully() throws Exception {
        ProxyResourceValidator.allowLoopbackForTesting = true;
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);

        byte[] jsBytes = "console.log('worker loaded');".getBytes(StandardCharsets.UTF_8);

        server.createContext("/service-worker.js", exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "application/javascript");
            exchange.sendResponseHeaders(200, jsBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(jsBytes);
            }
        });

        server.start();
        int port = server.getAddress().getPort();

        try {
            String upstreamBase = "http://127.0.0.1:" + port + "/";
            NewUrl proxyUrl = new NewUrl(
                    "pernias-home",
                    upstreamBase,
                    null,
                    "http://localhost/pernias-home",
                    Instant.now().plus(30, ChronoUnit.DAYS),
                    null,
                    LinkMode.PROXY
            );
            shortUrlRepository.save(proxyUrl);

            // Sub-resource requested without Referer but with PREONS_PROXY_CTX cookie
            jakarta.servlet.http.Cookie proxyCookie = new jakarta.servlet.http.Cookie("PREONS_PROXY_CTX", "pernias-home");
            mockMvc.perform(get("/service-worker.js")
                            .cookie(proxyCookie)
                            .header("User-Agent", "Mozilla/5.0"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Content-Type", "application/javascript"))
                    .andExpect(content().bytes(jsBytes));

        } finally {
            ProxyResourceValidator.allowLoopbackForTesting = false;
            server.stop(0);
        }
    }
}
